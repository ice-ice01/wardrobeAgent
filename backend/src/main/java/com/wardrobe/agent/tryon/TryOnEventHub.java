package com.wardrobe.agent.tryon;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
/** 进程内 SSE 订阅中心；ConcurrentHashMap 和 CopyOnWriteArrayList 支持并发订阅与广播。 */
public class TryOnEventHub {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    /** 注册连接并立即发送当前快照，避免订阅者必须等待下一次状态变化。 */
    public SseEmitter subscribe(String taskId, TryOnView snapshot) {
        return subscribe(taskId, snapshot, new SseEmitter(120_000L));
    }
    SseEmitter subscribe(String taskId, TryOnView snapshot, SseEmitter emitter) {
        emitters.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(exception -> remove(taskId, emitter));
        send(taskId, emitter, snapshot);
        return emitter;
    }
    public void publish(String taskId, TryOnView view) {
        emitters.getOrDefault(taskId, List.of()).forEach(emitter -> send(taskId, emitter, view));
    }
    private void send(String taskId, SseEmitter emitter, TryOnView view) {
        try {
            emitter.send(SseEmitter.event().id(view.id() + ":" + view.statusVersion()).name("task.status").data(view));
        } catch (IOException | IllegalStateException exception) {
            remove(taskId, emitter);
        }
    }
    private void remove(String taskId, SseEmitter emitter) {
        emitters.computeIfPresent(taskId, (key, subscribers) -> {
            subscribers.remove(emitter);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }
}
