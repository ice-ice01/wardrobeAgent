package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.model.UserModel;
import com.wardrobe.agent.model.UserModelService;
import com.wardrobe.agent.outfit.OutfitPlan;
import com.wardrobe.agent.outfit.OutfitPlanItem;
import com.wardrobe.agent.outfit.OutfitPlanItemRepository;
import com.wardrobe.agent.outfit.OutfitService;
import com.wardrobe.agent.outfit.OutfitStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TryOnService {
    private final OutfitService outfits;
    private final OutfitPlanItemRepository planItems;
    private final UserModelService models;
    private final TryOnConfirmationRepository confirmations;
    private final TryOnTaskRepository tasks;
    private final TryOnTaskItemRepository taskItems;
    private final TryOnEventHub events;
    private final ApplicationEventPublisher applicationEvents;
    private final TryOnProviderRegistry providers;
    private final String configuredProvider;
    private final boolean realEnabled;
    private final int dailyQuota;
    private final int userConcurrency;
    private final int globalConcurrency;
    private final boolean springAiFallbackEnabled;
    private final Duration leaseDuration;

    public TryOnService(OutfitService outfits, OutfitPlanItemRepository planItems, UserModelService models,
                        TryOnConfirmationRepository confirmations, TryOnTaskRepository tasks,
                        TryOnTaskItemRepository taskItems, TryOnEventHub events,
                        ApplicationEventPublisher applicationEvents,
                        TryOnProviderRegistry providers,
                        @Value("${app.tryon.provider:GPT_IMAGE_EDIT}") String configuredProvider,
                        @Value("${app.tryon.real-enabled:true}") boolean realEnabled,
                        @Value("${app.tryon.max-real-calls-per-user-day:3}") int dailyQuota,
                        @Value("${app.tryon.max-real-concurrency-per-user:1}") int userConcurrency,
                        @Value("${app.tryon.max-real-concurrency-global:3}") int globalConcurrency,
                        @Value("${app.tryon.spring-ai-fallback-enabled:false}") boolean springAiFallbackEnabled,
                        @Value("${app.tryon.lease-duration:PT4M}") Duration leaseDuration) {
        this.outfits = outfits;
        this.planItems = planItems;
        this.models = models;
        this.confirmations = confirmations;
        this.tasks = tasks;
        this.taskItems = taskItems;
        this.events = events;
        this.applicationEvents = applicationEvents;
        this.providers = providers;
        this.configuredProvider = configuredProvider;
        this.realEnabled = realEnabled;
        this.dailyQuota = dailyQuota;
        this.userConcurrency = userConcurrency;
        this.globalConcurrency = globalConcurrency;
        this.springAiFallbackEnabled = springAiFallbackEnabled;
        this.leaseDuration = leaseDuration;
    }

    @Transactional(readOnly = true)
    public CapabilitiesView capabilities(String userId, String planId, int planVersion) {
        OutfitPlan plan = outfits.require(userId, planId);
        if (plan.getPlanVersion() != planVersion) {
            throw new BusinessException(HttpStatus.CONFLICT, "OUTFIT_VERSION_MISMATCH", "方案版本已变化，请刷新后重试");
        }
        TryOnProvider provider = providers.require(configuredProvider);
        var capability = provider.capabilities();
        boolean enabled = (!capability.real() || realEnabled) && provider.available();
        List<OutfitPlanItem> sourceItems = orderedPlanItems(plan.getId());
        List<CapabilityItem> supported = new ArrayList<>();
        List<CapabilityItem> unsupported = new ArrayList<>();
        for (OutfitPlanItem item : sourceItems) {
            CapabilityItem view = new CapabilityItem(item.getItemId(), item.getSlot(), item.getItemName(),
                    item.getImageUrl(), capability.supportedSlots().contains(item.getSlot()),
                    capability.supportedSlots().contains(item.getSlot()) ? null : unsupportedReason(item.getSlot()));
            if (view.supported()) supported.add(view); else unsupported.add(view);
        }
        int remainingToday = capability.real() ? Math.max(0, dailyQuota - (int) tasks.countByUserIdAndProviderAndCreatedAtAfter(
                userId, provider.code(), LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant())) : -1;
        boolean fallbackAvailable = springAiFallbackEnabled && "FASHN_V1_6".equals(provider.code())
                && providers.find("SPRING_AI_IMAGE").filter(TryOnProvider::available).isPresent();
        return new CapabilitiesView(provider.code(), capability.displayName(), capability.real() ? "REAL" : "MOCK",
                enabled, supported.size() > 1 ? "MULTI_STAGE" : "SINGLE", supported, unsupported,
                capability.estimatedMinSeconds(), capability.estimatedMaxSeconds(), capability.real(), remainingToday,
                fallbackAvailable, fallbackAvailable ? "SPRING_AI_IMAGE" : null,
                fallbackAvailable ? "AI_PREVIEW" : null);
    }

    @Transactional
    public ConfirmationView confirmation(String userId, ConfirmationRequest request) {
        OutfitPlan plan = outfits.require(userId, request.planId());
        if (plan.getStatus() != OutfitStatus.CONFIRMED || plan.getPlanVersion() != request.planVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "NEED_CONFIRMATION", "请先确认当前方案版本");
        }
        models.require(userId, request.userModelId());
        String providerCode = normalizeProvider(request.provider());
        if (providers.require(providerCode).capabilities().real() && !request.consent()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "真实试穿前需要确认第三方图片处理授权");
        }
        if (providers.require(providerCode).capabilities().real()) enforceRealLimits(userId, providerCode);
        Set<String> selected = validateSelection(plan.getId(), providerCode, request.selectedItemIds());
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
        Instant expiresAt = Instant.now().plusSeconds(300);
        confirmations.save(new TryOnConfirmation(userId, plan.getId(), plan.getPlanVersion(), request.userModelId(),
                providerCode, selected, hash(raw), expiresAt));
        return new ConfirmationView(raw, expiresAt);
    }

    @Transactional
    public TryOnView create(String userId, CreateTaskRequest request) {
        var existing = tasks.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey());
        if (existing.isPresent()) return view(existing.get());
        String tokenHash = hash(request.confirmationToken());
        TryOnConfirmation confirmation = confirmations.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "CONFIRMATION_REQUIRED", "试穿确认令牌无效"));
        if (!confirmation.getUserId().equals(userId) || confirmation.getConsumedAt() != null
                || confirmation.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CONFIRMATION_REQUIRED", "试穿确认令牌已过期或已使用");
        }
        OutfitPlan plan = outfits.require(userId, confirmation.getPlanId());
        Set<String> requested = canonical(request.selectedItemIds());
        if (plan.getPlanVersion() != confirmation.getPlanVersion()
                || !confirmation.getUserModelId().equals(request.userModelId())
                || !confirmation.getSelectedItemIds().equals(requested)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONFIRMATION_MISMATCH", "确认令牌与方案、形象或所选商品不匹配");
        }
        models.require(userId, request.userModelId());
        List<OutfitPlanItem> sourceItems = orderedPlanItems(plan.getId());
        Set<String> allIds = sourceItems.stream().map(OutfitPlanItem::getItemId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String coverage = requested.size() > 1 ? "MULTI_STAGE" : "SINGLE";
        String fingerprint = hash(userId + plan.getId() + plan.getPlanVersion() + request.userModelId()
                + confirmation.getProvider() + String.join(",", requested));
        TryOnTask task = tasks.save(new TryOnTask(userId, plan.getId(), plan.getPlanVersion(), request.userModelId(),
                confirmation.getProvider(), request.idempotencyKey(), tokenHash, fingerprint, coverage, requested, allIds,
                request.simulateFailure(), request.retryFromTaskId()));
        int sequence = 1;
        for (OutfitPlanItem item : sourceItems) {
            boolean selected = requested.contains(item.getItemId());
            taskItems.save(new TryOnTaskItem(task.getId(), item, selected, selected ? sequence++ : null));
        }
        confirmation.consume();
        task.transition(TryOnStatus.SUBMITTING);
        task.transition(TryOnStatus.SUBMITTED);
        TryOnView result = view(task);
        applicationEvents.publishEvent(new TryOnTaskSubmittedEvent(task.getId()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<TryOnView> list(String userId) {
        return tasks.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public TryOnView get(String userId, String id) { return view(require(userId, id)); }

    @Transactional
    public TryOnView retry(String userId, String id, RetryRequest request) {
        TryOnTask previous = require(userId, id);
        if (!Set.of(TryOnStatus.FAILED, TryOnStatus.TIMEOUT, TryOnStatus.PARTIALLY_SUCCEEDED).contains(previous.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "TASK_NOT_RETRYABLE", "只有失败、超时或部分成功任务可以重试");
        }
        return create(userId, new CreateTaskRequest(request.confirmationToken(), request.idempotencyKey(),
                previous.getUserModelId(), previous.getSelectedItemIds(), request.simulateFailure(), previous.getId()));
    }

    @Transactional
    public boolean claimTask(String taskId, String owner) {
        Instant now = Instant.now();
        return tasks.claim(taskId, owner, now, now.plus(leaseDuration),
                Set.of(TryOnStatus.CREATED, TryOnStatus.SUBMITTING, TryOnStatus.SUBMITTED, TryOnStatus.PROCESSING)) == 1;
    }

    @Transactional
    public void scheduleRetry(String taskId, String code, String message) {
        TryOnTask task = tasks.findById(taskId).orElse(null);
        if (task == null || isTerminal(task.getStatus())) return;
        if (task.getAttemptCount() >= 3) task.fail(code, message);
        else task.scheduleRetry(Instant.now().plusSeconds((long) Math.pow(2, task.getAttemptCount())), code, message);
        events.publish(taskId, view(task));
    }

    @Transactional
    public StageCommand startNextStage(String taskId) {
        TryOnTask task = tasks.findById(taskId).orElse(null);
        if (task == null || isTerminal(task.getStatus())) return null;
        List<TryOnTaskItem> ordered = taskItems.findAllByTaskIdOrderByStageSequenceAsc(taskId);
        TryOnTaskItem stage = ordered.stream()
                .filter(item -> item.getStageStatus() == TryOnStageStatus.PROCESSING).findFirst().orElse(null);
        if (stage != null) {
            task.renewLease(Instant.now().plus(leaseDuration));
            return stageCommand(task, stage, stage.getInputImageUrl(),
                    stage.getEffectiveProvider() == null ? task.getProvider() : stage.getEffectiveProvider(),
                    stage.getProviderTaskId(), stage.isFallbackUsed(), stage.getFallbackReason());
        }
        stage = ordered.stream().filter(item -> item.getStageStatus() == TryOnStageStatus.SUBMITTING).findFirst().orElse(null);
        if (stage != null) {
            task.submissionUnknown("图片请求可能已被 Provider 接收，已停止自动重提以避免重复扣费");
            events.publish(taskId, view(task));
            return null;
        }
        stage = ordered.stream().filter(item -> item.getStageStatus() == TryOnStageStatus.PENDING).findFirst().orElse(null);
        if (stage == null) return null;
        UserModel model = models.require(task.getUserId(), task.getUserModelId());
        String inputImageUrl = task.getResultImageUrl() == null ? model.getImageUrl() : task.getResultImageUrl();
        stage.start(inputImageUrl, task.getProvider(), TryOnResultKinds.forProvider(task.getProvider()));
        task.transition(TryOnStatus.PROCESSING);
        task.renewLease(Instant.now().plus(leaseDuration));
        events.publish(taskId, view(task));
        return stageCommand(task, stage, inputImageUrl, task.getProvider(), null, false, null);
    }

    private StageCommand stageCommand(TryOnTask task, TryOnTaskItem stage, String inputImageUrl,
                                      String provider, String providerTaskId, boolean fallbackUsed, String fallbackReason) {
        return new StageCommand(task.getId(), stage.getId(), task.getUserId(), provider, task.getProvider(), stage.getItemId(),
                stage.getItemName(), stage.getSlot(), inputImageUrl, stage.getImageUrl(), task.isSimulateFailure(),
                providerTaskId, fallbackUsed, fallbackReason);
    }

    @Transactional
    public void stageSubmitted(String taskId, String stageId, TryOnProvider.ProviderSubmission submission) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.submitted(submission.providerTaskId(), submission.providerStatus());
        task.transition(TryOnStatus.PROCESSING);
        task.renewLease(Instant.now().plus(leaseDuration));
        events.publish(taskId, view(task));
    }

    @Transactional
    public void stageSubmissionUnknown(String taskId, String stageId, String message) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.fail("SUBMISSION_UNKNOWN", message);
        task.submissionUnknown(message);
        events.publish(taskId, view(task));
    }

    @Transactional
    public void stageProcessing(String taskId, String stageId, String providerStatus) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.processing(providerStatus);
        task.transition(TryOnStatus.PROCESSING);
        task.renewLease(Instant.now().plus(leaseDuration));
        events.publish(taskId, view(task));
    }

    @Transactional
    public void stageFallbackStarted(String taskId, String stageId, String provider, String reason) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.fallback(provider, reason);
        task.fallbackStarted(provider, reason);
        task.renewLease(Instant.now().plus(leaseDuration));
        events.publish(taskId, view(task));
    }

    @Transactional
    public void stageSucceeded(String taskId, String stageId, String resultImageUrl, String effectiveProvider,
                               String resultKind, boolean fallbackUsed, String fallbackReason) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.succeed(resultImageUrl);
        task.stageSucceeded(stage.getItemId(), resultImageUrl, effectiveProvider, resultKind, fallbackUsed, fallbackReason);
        task.renewLease(Instant.now().plus(leaseDuration));
        events.publish(taskId, view(task));
    }

    @Transactional
    public void stageFailed(String taskId, String stageId, String code, String message) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        TryOnTaskItem stage = taskItems.findById(stageId).orElseThrow();
        stage.fail(code, message);
        if (task.hasRenderedItems()) task.partial(code, message); else task.fail(code, message);
        task.clearLease();
        events.publish(taskId, view(task));
    }

    @Transactional
    public void completeTask(String taskId) {
        TryOnTask task = tasks.findById(taskId).orElseThrow();
        if (isTerminal(task.getStatus())) return;
        List<TryOnTaskItem> items = taskItems.findAllByTaskIdOrderByStageSequenceAsc(taskId);
        long selectedCount = items.stream().filter(TryOnTaskItem::isSelected).count();
        long succeededCount = items.stream().filter(TryOnTaskItem::isSelected)
                .filter(item -> item.getStageStatus() == TryOnStageStatus.SUCCEEDED).count();
        if (selectedCount == 0 || selectedCount != succeededCount) {
            task.fail("TRYON_INCOMPLETE", "试穿阶段没有全部完成");
        } else {
            String coverage = selectedCount == items.size() ? "FULL_OUTFIT" : selectedCount > 1 ? "MULTI_STAGE" : "SINGLE";
            task.succeed(task.getResultImageUrl(), coverage);
        }
        task.clearLease();
        events.publish(taskId, view(task));
    }

    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(String userId, String id) {
        TryOnTask task = require(userId, id);
        return events.subscribe(id, view(task));
    }

    private Set<String> validateSelection(String planId, String providerCode, Set<String> requestedIds) {
        Set<String> requested = canonical(requestedIds);
        if (requested.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "TRYON_ITEMS_REQUIRED", "请至少选择一件试穿商品");
        TryOnProvider provider = providers.require(providerCode);
        Set<String> supportedSlots = provider.capabilities().supportedSlots();
        List<OutfitPlanItem> sourceItems = orderedPlanItems(planId);
        Map<String, OutfitPlanItem> byId = sourceItems.stream().collect(java.util.stream.Collectors.toMap(
                OutfitPlanItem::getItemId, item -> item));
        for (String itemId : requested) {
            OutfitPlanItem item = byId.get(itemId);
            if (item == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "TRYON_ITEM_NOT_IN_PLAN", "所选商品不属于当前方案");
            if (!supportedSlots.contains(item.getSlot())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_UNSUPPORTED", "当前 Provider 不支持：" + item.getItemName());
            }
        }
        boolean dress = requested.stream().map(byId::get).anyMatch(item -> "DRESS".equals(item.getSlot()));
        boolean separates = requested.stream().map(byId::get)
                .anyMatch(item -> Set.of("INNER_TOP", "BOTTOM").contains(item.getSlot()));
        if (dress && separates) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRYON_ITEM_CONFLICT", "连衣裙不能与上装或下装同时试穿");
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        sourceItems.stream().filter(item -> requested.contains(item.getItemId())).forEach(item -> ordered.add(item.getItemId()));
        return ordered;
    }

    private String normalizeProvider(String provider) {
        String value = provider == null || provider.isBlank() ? configuredProvider : provider.trim().toUpperCase();
        TryOnProvider selected = providers.require(value);
        if (!selected.code().equalsIgnoreCase(configuredProvider)) {
            throw new BusinessException(HttpStatus.CONFLICT, "PROVIDER_NOT_ACTIVE", "Provider 与当前环境配置不一致");
        }
        if (selected.capabilities().real() && !realEnabled) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "REAL_PROVIDER_DISABLED", "真实试穿当前未开启");
        }
        if (!selected.available()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", "当前图片 Provider 未正确配置");
        }
        return selected.code();
    }

    private void enforceRealLimits(String userId, String provider) {
        Set<TryOnStatus> active = Set.of(TryOnStatus.CREATED, TryOnStatus.SUBMITTING, TryOnStatus.SUBMITTED,
                TryOnStatus.PROCESSING, TryOnStatus.SUBMISSION_UNKNOWN);
        Instant dayStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        if (tasks.countByUserIdAndProviderAndCreatedAtAfter(userId, provider, dayStart) >= dailyQuota) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TRYON_DAILY_QUOTA_EXCEEDED", "今日真实试穿额度已用完");
        }
        if (tasks.countByUserIdAndProviderAndStatusIn(userId, provider, active) >= userConcurrency
                || tasks.countByProviderAndStatusIn(provider, active) >= globalConcurrency) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "TRYON_CONCURRENCY_LIMIT", "真实试穿任务正在处理中，请稍后再试");
        }
    }

    private List<OutfitPlanItem> orderedPlanItems(String planId) {
        return planItems.findAllByPlanIdOrderByLayerOrderAsc(planId).stream()
                .sorted(Comparator.comparingInt(item -> stageOrder(item.getSlot())))
                .toList();
    }

    private int stageOrder(String slot) {
        return switch (slot) {
            case "DRESS", "INNER_TOP" -> 10;
            case "BOTTOM" -> 20;
            case "OUTER_TOP" -> 30;
            default -> 100;
        };
    }

    private String unsupportedReason(String slot) {
        return switch (slot) {
            case "SHOES" -> "当前 Provider 不支持鞋履";
            case "ACCESSORY", "BAG" -> "当前 Provider 不支持配饰";
            default -> "当前 Provider 不支持此商品";
        };
    }

    private TryOnTask require(String userId, String id) {
        return tasks.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "TRYON_TASK_NOT_FOUND", "试穿任务不存在"));
    }

    private TryOnView view(TryOnTask task) {
        List<TryOnView.Item> items = taskItems.findAllByTaskIdOrderByStageSequenceAsc(task.getId()).stream()
                .sorted(Comparator.comparing((TryOnTaskItem item) -> item.getStageSequence() == null ? Integer.MAX_VALUE : item.getStageSequence()))
                .map(item -> new TryOnView.Item(item.getItemId(), item.getSlot(), item.getItemName(), item.getImageUrl(),
                        item.isSelected(), item.isRendered(), item.getStageSequence(), item.getStageStatus(),
                        item.getEffectiveProvider(), item.isFallbackUsed(), item.getFallbackReason(), item.getResultKind(),
                        item.getResultImageUrl(), item.getErrorCode(), item.getErrorMessage())).toList();
        return new TryOnView(task.getId(), task.getPlanId(), task.getPlanVersion(), task.getUserModelId(), task.getProvider(),
                task.getEffectiveProvider(), task.isFallbackUsed(), task.getFallbackReason(), task.getResultKind(),
                task.getStatus(), task.getTryOnCoverage(), task.getSelectedItemIds(), task.getRenderedItemIds(),
                task.getUnrenderedItemIds(), task.getResultImageUrl(), task.getErrorCode(), task.getErrorMessage(),
                task.getStatusVersion(), task.getRetryFromTaskId(), items, task.getCreatedAt());
    }

    private boolean isTerminal(TryOnStatus status) {
        return Set.of(TryOnStatus.SUCCEEDED, TryOnStatus.SUCCEEDED_LATE, TryOnStatus.PARTIALLY_SUCCEEDED,
                TryOnStatus.FAILED, TryOnStatus.TIMEOUT, TryOnStatus.CANCELLED, TryOnStatus.SUBMISSION_UNKNOWN).contains(status);
    }

    private Set<String> canonical(Set<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private byte[] randomBytes(int count) {
        byte[] value = new byte[count];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record ConfirmationRequest(
            @jakarta.validation.constraints.NotBlank String planId,
            @jakarta.validation.constraints.Positive int planVersion,
            @jakarta.validation.constraints.NotBlank String userModelId,
            @jakarta.validation.constraints.NotBlank String provider,
            @jakarta.validation.constraints.NotEmpty Set<String> selectedItemIds,
            boolean consent
    ) {}
    public record ConfirmationView(String token, Instant expiresAt) {}
    public record CreateTaskRequest(
            @jakarta.validation.constraints.NotBlank String confirmationToken,
            @jakarta.validation.constraints.NotBlank String idempotencyKey,
            @jakarta.validation.constraints.NotBlank String userModelId,
            @jakarta.validation.constraints.NotEmpty Set<String> selectedItemIds,
            boolean simulateFailure,
            String retryFromTaskId
    ) {}
    public record RetryRequest(
            @jakarta.validation.constraints.NotBlank String confirmationToken,
            @jakarta.validation.constraints.NotBlank String idempotencyKey,
            boolean simulateFailure
    ) {}
    public record CapabilitiesView(String providerCode, String displayName, String mode, boolean enabled,
                                   String coverage, List<CapabilityItem> supportedItems,
                                   List<CapabilityItem> unsupportedItems, int estimatedMinSeconds,
                                   int estimatedMaxSeconds, boolean requiresExplicitConsent, int remainingToday,
                                   boolean fallbackEnabled, String fallbackProviderCode, String fallbackResultKind) {}
    public record CapabilityItem(String itemId, String slot, String name, String imageUrl,
                                 boolean supported, String reason) {}
    public record StageCommand(String taskId, String stageId, String userId, String provider, String requestedProvider, String itemId,
                               String itemName, String slot, String inputImageUrl, String garmentImageUrl,
                               boolean simulateFailure, String providerTaskId, boolean fallbackUsed, String fallbackReason) {
        public StageCommand(String taskId, String stageId, String userId, String provider, String itemId,
                            String itemName, String slot, String inputImageUrl, String garmentImageUrl,
                            boolean simulateFailure) {
            this(taskId, stageId, userId, provider, provider, itemId, itemName, slot, inputImageUrl,
                    garmentImageUrl, simulateFailure, null, false, null);
        }
    }
}
