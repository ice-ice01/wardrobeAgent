package com.wardrobe.agent.agent;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AgentRunEventHub {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(AgentRunView snapshot, List<AgentEvent> replay) {
        return subscribe(snapshot, replay, new SseEmitter(180_000L));
    }

    SseEmitter subscribe(AgentRunView snapshot, List<AgentEvent> replay, SseEmitter emitter) {
        emitters.computeIfAbsent(snapshot.id(), key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(snapshot.id(), emitter));
        emitter.onTimeout(() -> remove(snapshot.id(), emitter));
        emitter.onError(exception -> remove(snapshot.id(), emitter));
        if (!sendStatus(snapshot.id(), emitter, snapshot)) return emitter;
        for (AgentEvent event : replay) {
            if (!sendEvent(snapshot.id(), emitter, snapshot.attemptCount(), event)) break;
        }
        return emitter;
    }

    public void publishStatus(AgentRunView view) {
        emitters.getOrDefault(view.id(), List.of()).forEach(emitter -> sendStatus(view.id(), emitter, view));
    }

    public void publishEvent(String runId, int attemptNumber, AgentEvent event) {
        emitters.getOrDefault(runId, List.of()).forEach(emitter -> sendEvent(runId, emitter, attemptNumber, event));
    }

    private boolean sendStatus(String runId, SseEmitter emitter, AgentRunView view) {
        return send(runId, emitter, SseEmitter.event().id(view.attemptCount() + ":0").name("run.status").data(view));
    }

    private boolean sendEvent(String runId, SseEmitter emitter, int attemptNumber, AgentEvent event) {
        return send(runId, emitter,
                SseEmitter.event().id(attemptNumber + ":" + event.sequence()).name("agent.event").data(event));
    }

    private boolean send(String runId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(runId, emitter);
            return false;
        }
    }

    private void remove(String runId, SseEmitter emitter) {
        emitters.computeIfPresent(runId, (key, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
