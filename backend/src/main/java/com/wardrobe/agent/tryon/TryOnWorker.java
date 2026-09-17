package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.media.MediaStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
public class TryOnWorker {
    private final TryOnService service;
    private final TryOnProviderRegistry providers;
    private final MediaStorageService media;
    private final Duration pollInterval;
    private final Duration stageTimeout;
    private final boolean springAiFallbackEnabled;

    public TryOnWorker(TryOnService service, TryOnProviderRegistry providers, MediaStorageService media,
                       @Value("${app.tryon.poll-interval:PT2S}") Duration pollInterval,
                       @Value("${app.tryon.stage-timeout:PT90S}") Duration stageTimeout,
                       @Value("${app.tryon.spring-ai-fallback-enabled:false}") boolean springAiFallbackEnabled) {
        this.service = service;
        this.providers = providers;
        this.media = media;
        this.pollInterval = pollInterval;
        this.stageTimeout = stageTimeout;
        this.springAiFallbackEnabled = springAiFallbackEnabled;
    }

    @Async
    public void start(String taskId) {
        String owner = "tryon-" + UUID.randomUUID();
        if (!service.claimTask(taskId, owner)) return;
        try {
            while (true) {
                TryOnService.StageCommand stage = service.startNextStage(taskId);
                if (stage == null) {
                    service.completeTask(taskId);
                    return;
                }
                if (!runStage(stage)) return;
            }
        } catch (Exception exception) {
            service.scheduleRetry(taskId, "TRYON_WORKER_INTERRUPTED", safeMessage(exception));
        }
    }

    private boolean runStage(TryOnService.StageCommand stage) {
        TryOnProvider primary = providers.require(stage.provider());
        Attempt primaryAttempt = execute(stage, primary, stage.fallbackUsed(), stage.fallbackReason());
        if (primaryAttempt.succeeded()) return true;
        if (primaryAttempt.submissionUnknown()) return false;
        if (!stage.fallbackUsed() && canFallback(stage, primaryAttempt)) {
            TryOnProvider fallback = providers.require("SPRING_AI_IMAGE");
            String reason = primaryAttempt.code() + ": " + primaryAttempt.message();
            service.stageFallbackStarted(stage.taskId(), stage.stageId(), fallback.code(), truncate(reason, 500));
            Attempt fallbackAttempt = execute(stage, fallback, true, reason);
            if (fallbackAttempt.succeeded()) return true;
            service.stageFailed(stage.taskId(), stage.stageId(), primaryAttempt.code(),
                    truncate(primaryAttempt.message() + "；Spring AI 效果预览兜底失败：" + fallbackAttempt.message(), 500));
        } else {
            service.stageFailed(stage.taskId(), stage.stageId(), primaryAttempt.code(), primaryAttempt.message());
        }
        return false;
    }

    private Attempt execute(TryOnService.StageCommand stage, TryOnProvider provider,
                            boolean fallbackUsed, String fallbackReason) {
        try {
            boolean real = provider.capabilities().real();
            String modelImage = real ? media.toDataUri(stage.userId(), stage.inputImageUrl()) : stage.inputImageUrl();
            String garmentImage = real ? media.toDataUri(stage.userId(), stage.garmentImageUrl()) : stage.garmentImageUrl();
            TryOnProvider.ProviderSubmission submission = null;
            TryOnProvider.ProviderResult result;
            String providerTaskId = stage.providerTaskId();
            if (providerTaskId == null) {
                try {
                    submission = provider.submit(new TryOnProvider.ProviderRequest(
                            modelImage, garmentImage, category(stage.slot()), stage.itemName(), stage.slot(), stage.simulateFailure()));
                } catch (org.springframework.web.client.ResourceAccessException exception) {
                    String message = "提交连接中断，无法确认 Provider 是否已接收；为避免重复扣费已停止自动重提";
                    service.stageSubmissionUnknown(stage.taskId(), stage.stageId(), message);
                    return new Attempt(false, "SUBMISSION_UNKNOWN", message, false, true);
                }
                service.stageSubmitted(stage.taskId(), stage.stageId(), submission);
                providerTaskId = submission.providerTaskId();
                result = submission.immediateResult();
            } else {
                result = provider.query(providerTaskId);
            }
            Instant deadline = Instant.now().plus(stageTimeout);
            while (result == null && Instant.now().isBefore(deadline)) {
                sleep(pollInterval);
                result = provider.query(providerTaskId);
                if (result.state() == TryOnProvider.ProviderResult.State.PROCESSING) {
                    service.stageProcessing(stage.taskId(), stage.stageId(), result.providerStatus());
                    result = null;
                }
            }
            if (result == null) return new Attempt(false, "PROVIDER_TIMEOUT", "试穿阶段处理超时", true, false);
            if (result.state() == TryOnProvider.ProviderResult.State.FAILED) {
                String code = result.errorCode() == null ? "PROVIDER_GENERATION_FAILED" : result.errorCode();
                return new Attempt(false, code, safe(result.errorMessage()), fallbackAllowed(code, result.errorMessage(), null), false);
            }
            String resultUrl = result.outputUrl();
            if (result.outputDataUri() != null) {
                resultUrl = media.storeGeneratedDataUri(stage.userId(), result.outputDataUri()).url();
            } else if (real) {
                return new Attempt(false, "PROVIDER_BASE64_REQUIRED", "图片 Provider 未返回可持久化的 Base64 结果", true, false);
            }
            String kind = TryOnResultKinds.forProvider(provider.code());
            service.stageSucceeded(stage.taskId(), stage.stageId(), resultUrl, provider.code(), kind,
                    fallbackUsed, truncate(fallbackReason, 500));
            return new Attempt(true, null, null, false, false);
        } catch (Exception exception) {
            String code = exception instanceof BusinessException business ? business.getCode() : httpCode(exception);
            return new Attempt(false, code, safeMessage(exception), fallbackAllowed(code, exception.getMessage(), exception), false);
        }
    }

    private boolean canFallback(TryOnService.StageCommand stage, Attempt failure) {
        return springAiFallbackEnabled && "FASHN_V1_6".equals(stage.requestedProvider()) && failure.fallbackAllowed()
                && providers.find("SPRING_AI_IMAGE").filter(TryOnProvider::available).isPresent();
    }

    private boolean fallbackAllowed(String code, String message, Exception exception) {
        if (exception instanceof BusinessException) return false;
        if (exception instanceof org.springframework.web.client.RestClientResponseException response
                && (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 422)) return false;
        String classification = ((code == null ? "" : code) + " " + (message == null ? "" : message)).toUpperCase(Locale.ROOT);
        return java.util.stream.Stream.of("MODERATION", "SAFETY", "NSFW", "CONTENT_POLICY", "INVALID_INPUT",
                        "INVALID_IMAGE", "UNCONTROLLED_IMAGE", "CONSENT", "OWNERSHIP", "FORBIDDEN_RESOURCE")
                .noneMatch(classification::contains);
    }

    private String httpCode(Exception exception) {
        if (exception instanceof org.springframework.web.client.RestClientResponseException response) {
            return "PROVIDER_HTTP_" + response.getStatusCode().value();
        }
        return "PROVIDER_CALL_FAILED";
    }

    private String category(String slot) {
        return switch (slot) {
            case "BOTTOM" -> "bottoms";
            case "DRESS" -> "one-pieces";
            default -> "tops";
        };
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "试穿 Provider 调用失败" : message.substring(0, Math.min(400, message.length()));
    }

    private String safe(String message) {
        return message == null || message.isBlank() ? "图片 Provider 生成失败" : truncate(message, 400);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.substring(0, Math.min(max, value.length()));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(0, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试穿任务被中断", exception);
        }
    }

    private record Attempt(boolean succeeded, String code, String message, boolean fallbackAllowed, boolean submissionUnknown) {}
}
