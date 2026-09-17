package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "agent_run", uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_run_client", columnNames = {"user_id", "client_message_id"}))
public class AgentRun extends AuditedEntity {
    @Column(name = "user_id", nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String conversationId;
    @Column(name = "client_message_id", nullable = false, length = 80) private String clientMessageId;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json")
    private Set<String> lockedItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json")
    private Set<String> excludedItemIds = new LinkedHashSet<>();
    @Column(length = 30) private String action;
    @Column(length = 40) private String sourceOutfitId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AgentRunStatus status;
    @Column(nullable = false) private int attemptCount;
    @Column(length = 80) private String leaseOwner;
    private Instant leaseUntil;
    private Instant nextAttemptAt;
    @Column(nullable = false) private long statusVersion;
    @Column(length = 80) private String errorCode;
    @Column(length = 500) private String errorMessage;

    protected AgentRun() {}

    public AgentRun(String userId, String conversationId, AgentMessageRequest request) {
        super("arun");
        this.userId = userId;
        this.conversationId = conversationId;
        this.clientMessageId = request.clientMessageId();
        this.content = request.content().trim();
        this.lockedItemIds = new LinkedHashSet<>(request.safeLocked());
        this.excludedItemIds = new LinkedHashSet<>(request.safeExcluded());
        this.action = request.action();
        this.sourceOutfitId = request.sourceOutfitId();
        this.status = AgentRunStatus.QUEUED;
    }

    public AgentMessageRequest request() {
        return new AgentMessageRequest(clientMessageId, content, lockedItemIds, excludedItemIds, action, sourceOutfitId);
    }

    public void complete() { status = AgentRunStatus.COMPLETED; statusVersion++; clearLease(); }
    public void fail(String code, String message) { status = AgentRunStatus.FAILED; statusVersion++; errorCode = code; errorMessage = message; clearLease(); }
    public void clearLease() { leaseOwner = null; leaseUntil = null; nextAttemptAt = null; }
    public String getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public String getClientMessageId() { return clientMessageId; }
    public String getContent() { return content; }
    public AgentRunStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public long getStatusVersion() { return statusVersion; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
