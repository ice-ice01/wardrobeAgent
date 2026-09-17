package com.wardrobe.agent.agent;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "recommendation_run")
/** 一次搭配推荐的审计快照，记录候选、约束和处理阶段，便于复盘 AI 输入。 */
public class RecommendationRun extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String conversationId;
    @Column(nullable = false, length = 80) private String clientMessageId;
    @Column(nullable = false, columnDefinition = "text") private String sceneSnapshot;
    @Column(nullable = false) private long wardrobeRevision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> candidateItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> lockedItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> excludedItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> shownCombinationKeys = new LinkedHashSet<>();
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 200) private String nextCursor;

    protected RecommendationRun() {}
    public RecommendationRun(String userId, String conversationId, String clientMessageId, String scene, long revision,
                             Set<String> candidates, Set<String> locked, Set<String> excluded) {
        super("run"); this.userId = userId; this.conversationId = conversationId; this.clientMessageId = clientMessageId;
        this.sceneSnapshot = scene; this.wardrobeRevision = revision; this.candidateItemIds = new LinkedHashSet<>(candidates);
        this.lockedItemIds = new LinkedHashSet<>(locked); this.excludedItemIds = new LinkedHashSet<>(excluded); this.status = "UNDERSTANDING";
    }
    public void status(String status) { this.status = status; }
    public void shown(String key) { shownCombinationKeys.add(key); }
    public String getId() { return super.getId(); }
    public String getClientMessageId() { return clientMessageId; }
    public String getStatus() { return status; }
}
