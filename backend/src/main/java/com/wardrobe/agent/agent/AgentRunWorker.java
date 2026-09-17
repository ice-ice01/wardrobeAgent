package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AgentRunWorker {
    private final AgentRunService runs;
    private final AgentService agent;

    public AgentRunWorker(AgentRunService runs, AgentService agent) {
        this.runs = runs; this.agent = agent;
    }

    @Async
    public void start(String runId) {
        AgentRun run = runs.claim(runId, "agent-" + UUID.randomUUID());
        if (run == null) return;
        try {
            agent.process(run.getUserId(), run.getConversationId(), run.request(),
                    event -> runs.recordEvent(runId, event));
            runs.complete(runId);
        } catch (Exception exception) {
            String code = exception instanceof BusinessException business ? business.getCode() : "AGENT_RUN_FAILED";
            runs.fail(runId, code, exception.getMessage());
        }
    }
}
