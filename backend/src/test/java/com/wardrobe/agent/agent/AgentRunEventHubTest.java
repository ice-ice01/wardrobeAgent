package com.wardrobe.agent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentRunEventHubTest {
    private final AgentRunEventHub eventHub = new AgentRunEventHub();

    @Test
    void completedSubscriberDoesNotBreakAgentEventPublishing() {
        AgentRunView snapshot = snapshot();
        var emitter = eventHub.subscribe(snapshot, List.of());
        emitter.complete();

        AgentEvent event = new AgentEvent(
                "event-1", "message.delta", "conversation-1", 1, Instant.now(), "你好");

        assertDoesNotThrow(() -> eventHub.publishEvent("run-1", 1, event));
        assertDoesNotThrow(() -> eventHub.publishStatus(snapshot));
    }

    @Test
    void disconnectedSubscriberDoesNotBreakAgentEventPublishing() {
        AgentRunView snapshot = snapshot();
        SseEmitter emitter = new SseEmitter() {
            private int sends;

            @Override
            public void send(SseEventBuilder builder) throws IOException {
                if (++sends > 1) throw new AsyncRequestNotUsableException("disconnected client");
            }
        };
        eventHub.subscribe(snapshot, List.of(), emitter);

        AgentEvent event = new AgentEvent(
                "event-1", "message.delta", "conversation-1", 1, Instant.now(), "你好");

        assertDoesNotThrow(() -> eventHub.publishEvent("run-1", 1, event));
        assertDoesNotThrow(() -> eventHub.publishStatus(snapshot));
    }

    private AgentRunView snapshot() {
        return new AgentRunView(
                "run-1", "conversation-1", "message-1", "推荐一套通勤穿搭",
                AgentRunStatus.RUNNING, 1, 1, null, null, Instant.now());
    }
}
