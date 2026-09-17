package com.wardrobe.agent.tryon;

import com.wardrobe.agent.media.MediaFileView;
import com.wardrobe.agent.media.MediaStorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TryOnWorkerFallbackTest {
    @Test
    void directSpringAiProviderProducesPreviewWithoutFallbackFlag() {
        TryOnService service = mock(TryOnService.class);
        MediaStorageService media = mock(MediaStorageService.class);
        var stage = new TryOnService.StageCommand("task-1", "stage-1", "user-1", "SPRING_AI_IMAGE", "item-1",
                "白色衬衫", "INNER_TOP", "/api/files/model", "/api/files/garment", false);
        when(service.startNextStage("task-1")).thenReturn(stage).thenReturn(null);
        when(service.claimTask(anyString(), anyString())).thenReturn(true);
        when(media.toDataUri(anyString(), anyString())).thenReturn("data:image/png;base64,aW5wdXQ=");
        when(media.storeGeneratedDataUri(anyString(), anyString()))
                .thenReturn(new MediaFileView("img-1", "TRYON_RESULT", "image/png", 10, "/api/files/img-1"));
        TryOnProvider springAi = succeededProvider();
        var worker = new TryOnWorker(service, new TryOnProviderRegistry(java.util.List.of(springAi)), media,
                Duration.ZERO, Duration.ofSeconds(1), false);

        worker.start("task-1");

        verify(service).stageSucceeded("task-1", "stage-1", "/api/files/img-1", "SPRING_AI_IMAGE",
                "AI_PREVIEW", false, null);
        verify(service).completeTask("task-1");
    }

    @Test
    void technicalFashnFailureFallsBackToSpringAiPreview() {
        TryOnService service = mock(TryOnService.class);
        MediaStorageService media = mock(MediaStorageService.class);
        var stage = stage();
        when(service.startNextStage("task-1")).thenReturn(stage).thenReturn(null);
        when(service.claimTask(anyString(), anyString())).thenReturn(true);
        when(media.toDataUri(anyString(), anyString())).thenReturn("data:image/png;base64,aW5wdXQ=");
        when(media.storeGeneratedDataUri(anyString(), anyString()))
                .thenReturn(new MediaFileView("img-1", "TRYON_RESULT", "image/png", 10, "/api/files/img-1"));
        TryOnProvider fashn = failedProvider("FASHN_V1_6", "FASHN_SERVICE_UNAVAILABLE", "service unavailable");
        TryOnProvider springAi = succeededProvider();
        var worker = new TryOnWorker(service, new TryOnProviderRegistry(java.util.List.of(fashn, springAi)), media,
                Duration.ZERO, Duration.ofSeconds(1), true);

        worker.start("task-1");

        verify(service).stageFallbackStarted("task-1", "stage-1", "SPRING_AI_IMAGE",
                "FASHN_SERVICE_UNAVAILABLE: service unavailable");
        verify(service).stageSucceeded("task-1", "stage-1", "/api/files/img-1", "SPRING_AI_IMAGE",
                "AI_PREVIEW", true, "FASHN_SERVICE_UNAVAILABLE: service unavailable");
        verify(service).completeTask("task-1");
    }

    @Test
    void moderationFailureDoesNotUseFallback() {
        TryOnService service = mock(TryOnService.class);
        MediaStorageService media = mock(MediaStorageService.class);
        when(service.startNextStage("task-1")).thenReturn(stage());
        when(service.claimTask(anyString(), anyString())).thenReturn(true);
        when(media.toDataUri(anyString(), anyString())).thenReturn("data:image/png;base64,aW5wdXQ=");
        TryOnProvider fashn = failedProvider("FASHN_V1_6", "MODERATION_REJECTED", "content policy rejected");
        TryOnProvider springAi = succeededProvider();
        var worker = new TryOnWorker(service, new TryOnProviderRegistry(java.util.List.of(fashn, springAi)), media,
                Duration.ZERO, Duration.ofSeconds(1), true);

        worker.start("task-1");

        verify(service, never()).stageFallbackStarted(anyString(), anyString(), anyString(), anyString());
        verify(service).stageFailed("task-1", "stage-1", "MODERATION_REJECTED", "content policy rejected");
    }

    @Test
    void ambiguousSubmissionStopsWithoutSubmittingAgain() {
        TryOnService service = mock(TryOnService.class);
        MediaStorageService media = mock(MediaStorageService.class);
        when(service.claimTask(anyString(), anyString())).thenReturn(true);
        when(service.startNextStage("task-1")).thenReturn(stage());
        when(media.toDataUri(anyString(), anyString())).thenReturn("data:image/png;base64,aW5wdXQ=");
        TryOnProvider provider = new ImmediateProvider("FASHN_V1_6", null) {
            @Override public ProviderSubmission submit(ProviderRequest request) {
                throw new org.springframework.web.client.ResourceAccessException("connection reset");
            }
        };
        var worker = new TryOnWorker(service, new TryOnProviderRegistry(java.util.List.of(provider)), media,
                Duration.ZERO, Duration.ofSeconds(1), false);

        worker.start("task-1");

        verify(service).stageSubmissionUnknown("task-1", "stage-1",
                "提交连接中断，无法确认 Provider 是否已接收；为避免重复扣费已停止自动重提");
        verify(service, never()).stageFailed(anyString(), anyString(), anyString(), anyString());
    }

    private TryOnService.StageCommand stage() {
        return new TryOnService.StageCommand("task-1", "stage-1", "user-1", "FASHN_V1_6", "item-1",
                "白色衬衫", "INNER_TOP", "/api/files/model", "/api/files/garment", false);
    }

    private TryOnProvider failedProvider(String code, String errorCode, String errorMessage) {
        return new ImmediateProvider(code, new TryOnProvider.ProviderResult(TryOnProvider.ProviderResult.State.FAILED,
                "failed", null, null, errorCode, errorMessage));
    }

    private TryOnProvider succeededProvider() {
        return new ImmediateProvider("SPRING_AI_IMAGE", new TryOnProvider.ProviderResult(
                TryOnProvider.ProviderResult.State.SUCCEEDED, "completed",
                "data:image/png;base64,aW1hZ2U=", null, null, null));
    }

    private static class ImmediateProvider implements TryOnProvider {
        private final String code;
        private final ProviderResult result;
        private ImmediateProvider(String code, ProviderResult result) { this.code = code; this.result = result; }
        @Override public String code() { return code; }
        @Override public ProviderCapabilities capabilities() {
            return new ProviderCapabilities(code, true, Set.of("INNER_TOP"), 1, 2);
        }
        @Override public ProviderSubmission submit(ProviderRequest request) {
            return new ProviderSubmission(code + "-task", result.providerStatus(), result);
        }
        @Override public ProviderResult query(String providerTaskId) { return result; }
    }
}
