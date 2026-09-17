package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TryOnEventHubTest {
    private final TryOnEventHub eventHub = new TryOnEventHub();

    @Test
    void completedSubscriberDoesNotBreakTaskPublishing() {
        TryOnView snapshot = mock(TryOnView.class);
        when(snapshot.id()).thenReturn("task-1");
        when(snapshot.statusVersion()).thenReturn(1L);
        var emitter = eventHub.subscribe("task-1", snapshot);
        emitter.complete();

        assertDoesNotThrow(() -> eventHub.publish("task-1", snapshot));
    }

    @Test
    void disconnectedSubscriberDoesNotBreakTaskPublishing() {
        TryOnView snapshot = mock(TryOnView.class);
        when(snapshot.id()).thenReturn("task-1");
        when(snapshot.statusVersion()).thenReturn(1L);
        SseEmitter emitter = new SseEmitter() {
            private int sends;

            @Override
            public void send(SseEventBuilder builder) throws IOException {
                if (++sends > 1) throw new AsyncRequestNotUsableException("disconnected client");
            }
        };
        eventHub.subscribe("task-1", snapshot, emitter);

        assertDoesNotThrow(() -> eventHub.publish("task-1", snapshot));
        assertDoesNotThrow(() -> eventHub.publish("task-1", snapshot));
    }
}
