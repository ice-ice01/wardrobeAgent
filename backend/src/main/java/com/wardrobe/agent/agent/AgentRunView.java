package com.wardrobe.agent.agent;

import java.time.Instant;

public record AgentRunView(String id, String conversationId, String clientMessageId, String content, AgentRunStatus status,
                           int attemptCount, long statusVersion, String errorCode, String errorMessage,
                           Instant createdAt) {
}
