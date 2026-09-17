package com.wardrobe.agent.agent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgentRunDispatcher {
    private final AgentRunWorker worker;
    public AgentRunDispatcher(AgentRunWorker worker) { this.worker = worker; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AgentRunSubmittedEvent event) { worker.start(event.runId()); }
}
