package com.wardrobe.agent.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunEventRepository extends JpaRepository<AgentRunEvent, String> {
    List<AgentRunEvent> findAllByRunIdAndAttemptNumberOrderBySequenceNumberAsc(String runId, int attemptNumber);
    List<AgentRunEvent> findAllByRunIdAndAttemptNumberAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
            String runId, int attemptNumber, long sequenceNumber);
}

