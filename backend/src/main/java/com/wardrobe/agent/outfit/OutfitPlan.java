package com.wardrobe.agent.outfit;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "outfit_plan")
/** 经 AI 建议和 Java 校验后持久化的搭配方案；@Version 用于并发更新的乐观锁。 */
public class OutfitPlan extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String conversationId;
    @Column(nullable = false, length = 40) private String recommendationRunId;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 500) private String scene;
    @Column(nullable = false) private int planVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OutfitStatus status;
    @Column(nullable = false) private boolean complete;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> missingSlots = new LinkedHashSet<>();
    @Column(nullable = false, columnDefinition = "text") private String reason;
    @Column(columnDefinition = "text") private String notes;
    @Column(nullable = false, length = 30) private String explorationStatus;
    @Column(nullable = false) private int wardrobeTotal;
    @Column(nullable = false) private int eligibleTotal;
    @Column(nullable = false) private int consideredCount;
    @Column(nullable = false) private boolean hasMore;
    @Column(nullable = false) private long wardrobeRevision;
    @Version private long rowVersion;

    protected OutfitPlan() {}
    public OutfitPlan(String userId, String conversationId, String runId, String title, String scene, boolean complete,
                      Set<String> missingSlots, String reason, String explorationStatus, int total, int eligible,
                      int considered, boolean hasMore, long wardrobeRevision) {
        super("plan"); this.userId = userId; this.conversationId = conversationId; this.recommendationRunId = runId;
        this.title = title; this.scene = scene; this.planVersion = 1; this.status = OutfitStatus.DRAFT; this.complete = complete;
        this.missingSlots = new LinkedHashSet<>(missingSlots); this.reason = reason; this.notes = "";
        this.explorationStatus = explorationStatus; this.wardrobeTotal = total; this.eligibleTotal = eligible;
        this.consideredCount = considered; this.hasMore = hasMore; this.wardrobeRevision = wardrobeRevision;
    }
    /** 通过实体行为改变状态，事务提交时由 JPA 脏检查执行 UPDATE。 */
    public void savePlan() { if (status == OutfitStatus.DRAFT) status = OutfitStatus.SAVED; }
    public void confirm() { if (status != OutfitStatus.SUPERSEDED) status = OutfitStatus.CONFIRMED; }
    public String getUserId() { return userId; }
    public String getConversationId() { return conversationId; }
    public String getRecommendationRunId() { return recommendationRunId; }
    public String getTitle() { return title; }
    public String getScene() { return scene; }
    public int getPlanVersion() { return planVersion; }
    public OutfitStatus getStatus() { return status; }
    public boolean isComplete() { return complete; }
    public Set<String> getMissingSlots() { return Set.copyOf(missingSlots); }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public String getExplorationStatus() { return explorationStatus; }
    public int getWardrobeTotal() { return wardrobeTotal; }
    public int getEligibleTotal() { return eligibleTotal; }
    public int getConsideredCount() { return consideredCount; }
    public boolean isHasMore() { return hasMore; }
    public long getWardrobeRevision() { return wardrobeRevision; }
}
