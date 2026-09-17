package com.wardrobe.agent.tryon;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class TryOnRecoveryScheduler {
    private static final Set<TryOnStatus> RECOVERABLE = Set.of(
            TryOnStatus.CREATED, TryOnStatus.SUBMITTING, TryOnStatus.SUBMITTED, TryOnStatus.PROCESSING);
    private final TryOnTaskRepository tasks;
    private final TryOnWorker worker;

    public TryOnRecoveryScheduler(TryOnTaskRepository tasks, TryOnWorker worker) {
        this.tasks = tasks;
        this.worker = worker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    @Scheduled(fixedDelayString = "${app.tryon.recovery-interval:PT5S}")
    public void recover() {
        tasks.findRecoverableIds(RECOVERABLE, Instant.now(), PageRequest.of(0, 20)).forEach(worker::start);
    }
}
