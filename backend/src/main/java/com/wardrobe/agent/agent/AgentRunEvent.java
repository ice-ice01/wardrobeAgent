package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "agent_run_event", uniqueConstraints = @UniqueConstraint(
        name = "uk_agent_run_event_sequence", columnNames = {"run_id", "attempt_number", "sequence_number"}))
public class AgentRunEvent extends AuditedEntity {
    @Column(nullable = false, length = 40) private String runId;
    @Column(nullable = false) private int attemptNumber;
    @Column(nullable = false) private long sequenceNumber;
    @Column(nullable = false, length = 40) private String eventId;
    @Column(nullable = false, length = 40) private String eventType;
    @Column(nullable = false, length = 40) private String conversationId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> eventData;

    protected AgentRunEvent() {}
    @SuppressWarnings("unchecked")
    public AgentRunEvent(String runId, int attemptNumber, AgentEvent event) {
        super("arevt");
        this.runId = runId; this.attemptNumber = attemptNumber; this.sequenceNumber = event.sequence();
        this.eventId = event.eventId(); this.eventType = event.type(); this.conversationId = event.conversationId();
        this.eventData = event.data() instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of("value", event.data());
    }
    public AgentEvent view() { return new AgentEvent(eventId, eventType, conversationId, sequenceNumber, getCreatedAt(), eventData); }
}
