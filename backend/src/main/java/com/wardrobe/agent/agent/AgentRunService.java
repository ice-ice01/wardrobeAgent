package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class AgentRunService {
    private final AgentRunRepository runs;
    private final AgentRunEventRepository runEvents;
    private final ConversationRepository conversations;
    private final AgentRunEventHub eventHub;
    private final ApplicationEventPublisher applicationEvents;
    private final Duration leaseDuration;

    public AgentRunService(AgentRunRepository runs, AgentRunEventRepository runEvents,
                           ConversationRepository conversations, AgentRunEventHub eventHub,
                           ApplicationEventPublisher applicationEvents,
                           @Value("${app.ai.run-lease-duration:PT3M}") Duration leaseDuration) {
        this.runs = runs; this.runEvents = runEvents; this.conversations = conversations;
        this.eventHub = eventHub; this.applicationEvents = applicationEvents; this.leaseDuration = leaseDuration;
    }

    @Transactional
    // 将任务持久化
    public AgentRunView create(String userId, String conversationId, AgentMessageRequest request) {
        requireConversation(userId, conversationId);
        var existing = runs.findByUserIdAndClientMessageId(userId, request.clientMessageId());
        if (existing.isPresent()) return view(existing.get());
        AgentRun run = runs.save(new AgentRun(userId, conversationId, request));
        applicationEvents.publishEvent(new AgentRunSubmittedEvent(run.getId()));
        return view(run);
    }

    @Transactional(readOnly = true)
    public AgentRunView get(String userId, String runId) { return view(require(userId, runId)); }

    @Transactional(readOnly = true)
    public List<AgentRunView> list(String userId, String conversationId) {
        requireConversation(userId, conversationId);
        return runs.findAllByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId).stream().map(this::view).toList();
    }

    @Transactional
    public AgentRun claim(String runId, String owner) {
        Instant now = Instant.now();
        if (runs.claim(runId, owner, now, now.plus(leaseDuration),
                Set.of(AgentRunStatus.QUEUED, AgentRunStatus.RUNNING)) != 1) return null;
        AgentRun run = runs.findById(runId).orElseThrow();
        eventHub.publishStatus(view(run));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(String runId, AgentEvent event) {
        AgentRun run = runs.findById(runId).orElseThrow();
        AgentRunEvent saved = runEvents.saveAndFlush(new AgentRunEvent(runId, run.getAttemptCount(), event));
        eventHub.publishEvent(runId, run.getAttemptCount(), saved.view());
    }

    @Transactional
    public void complete(String runId) {
        AgentRun run = runs.findById(runId).orElseThrow();
        run.complete();
        eventHub.publishStatus(view(run));
    }

    @Transactional
    public void fail(String runId, String code, String message) {
        AgentRun run = runs.findById(runId).orElseThrow();
        run.fail(code, message == null ? "对话生成失败" : message.substring(0, Math.min(500, message.length())));
        eventHub.publishStatus(view(run));
    }

    @Transactional(readOnly = true)
    public SseEmitter subscribe(String userId, String runId, String lastEventId) {
        AgentRun run = require(userId, runId);
        long after = replaySequence(lastEventId, run.getAttemptCount());
        List<AgentEvent> replay = runEvents
                .findAllByRunIdAndAttemptNumberAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                        runId, run.getAttemptCount(), after)
                .stream().map(AgentRunEvent::view).toList();
        return eventHub.subscribe(view(run), replay);
    }

    private long replaySequence(String value, int attempt) {
        if (value == null || value.isBlank()) return 0;
        try {
            String[] parts = value.split(":", 2);
            return Integer.parseInt(parts[0]) == attempt ? Long.parseLong(parts[1]) : 0;
        } catch (RuntimeException ignored) { return 0; }
    }

    private AgentRun require(String userId, String runId) {
        return runs.findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "AGENT_RUN_NOT_FOUND", "对话任务不存在"));
    }

    private void requireConversation(String userId, String conversationId) {
        conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "会话不存在"));
    }

    private AgentRunView view(AgentRun run) {
        return new AgentRunView(run.getId(), run.getConversationId(), run.getClientMessageId(), run.getContent(), run.getStatus(),
                run.getAttemptCount(), run.getStatusVersion(), run.getErrorCode(), run.getErrorMessage(), run.getCreatedAt());
    }
}
