package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.PrefixIds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockTryOnProvider implements TryOnProvider {
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Duration completionDelay;

    public MockTryOnProvider(@Value("${app.tryon.completion-delay:PT8S}") Duration completionDelay) {
        this.completionDelay = completionDelay;
    }

    @Override public String code() { return "MOCK"; }
    @Override public ProviderCapabilities capabilities() {
        return new ProviderCapabilities("模拟试穿", false,
                Set.of("INNER_TOP", "OUTER_TOP", "BOTTOM", "DRESS"), 2, 8);
    }
    @Override public ProviderSubmission submit(ProviderRequest request) {
        String id = PrefixIds.next("mockpred");
        jobs.put(id, new Job(Instant.now(), request.simulateFailure()));
        return new ProviderSubmission(id, "starting");
    }
    @Override public ProviderResult query(String providerTaskId) {
        Job job = jobs.get(providerTaskId);
        if (job == null) return new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                "MOCK_TASK_NOT_FOUND", "模拟任务不存在");
        if (Duration.between(job.startedAt(), Instant.now()).compareTo(completionDelay) < 0) {
            return new ProviderResult(ProviderResult.State.PROCESSING, "processing", null, null, null, null);
        }
        if (job.fail()) return new ProviderResult(ProviderResult.State.FAILED, "failed", null, null,
                "MOCK_GENERATION_FAILED", "模拟试穿失败，可重新确认后重试");
        return new ProviderResult(ProviderResult.State.SUCCEEDED, "completed", null,
                "/assets/seed/tryon-result.png", null, null);
    }

    private record Job(Instant startedAt, boolean fail) {}
}
