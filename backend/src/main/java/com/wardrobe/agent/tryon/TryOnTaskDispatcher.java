package com.wardrobe.agent.tryon;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TryOnTaskDispatcher {
    private final TryOnWorker worker;

    public TryOnTaskDispatcher(TryOnWorker worker) {
        this.worker = worker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(TryOnTaskSubmittedEvent event) {
        worker.start(event.taskId());
    }
}
