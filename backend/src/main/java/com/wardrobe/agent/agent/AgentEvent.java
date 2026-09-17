package com.wardrobe.agent.agent;

import java.time.Instant;

/** Agent 流式协议事件；sequence 保证同一响应内有序，data 的结构由 type 决定。 */
public record AgentEvent(String eventId, String type, String conversationId, long sequence, Instant timestamp, Object data) {}
