package com.wardrobe.agent.agent;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class AgentRunRecoveryScheduler {
    private final AgentRunRepository runs;
    private final AgentRunWorker worker;
    public AgentRunRecoveryScheduler(AgentRunRepository runs, AgentRunWorker worker) { this.runs = runs; this.worker = worker; }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() { recover(); }

    @Scheduled(fixedDelayString = "${app.ai.run-recovery-interval:PT5S}")
    public void recover() {
        runs.findRecoverableIds(Set.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING), Instant.now(), PageRequest.of(0, 20))
                .forEach(worker::start);
    }
}
