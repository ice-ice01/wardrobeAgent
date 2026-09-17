package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.AuditedEntity;
import com.wardrobe.agent.outfit.OutfitPlanItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tryon_task_item")
/** 试穿任务创建时保存的方案商品快照，并标记本次 Provider 是否实际渲染该商品。 */
public class TryOnTaskItem extends AuditedEntity {
    @Column(nullable = false, length = 40) private String taskId;
    @Column(nullable = false, length = 40) private String itemId;
    @Column(nullable = false, length = 20) private String slot;
    @Column(nullable = false, length = 100) private String itemName;
    @Column(nullable = false, length = 500) private String imageUrl;
    @Column(nullable = false) private boolean rendered;
    @Column(nullable = false) private boolean selected;
    private Integer stageSequence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TryOnStageStatus stageStatus;
    @Column(length = 30) private String effectiveProvider;
    @Column(nullable = false) private boolean fallbackUsed;
    @Column(length = 500) private String fallbackReason;
    @Column(length = 30) private String resultKind;
    @Column(length = 120) private String providerTaskId;
    @Column(length = 80) private String providerStatus;
    @Column(length = 500) private String inputImageUrl;
    @Column(length = 500) private String resultImageUrl;
    @Column(length = 80) private String errorCode;
    @Column(length = 500) private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;

    protected TryOnTaskItem() {}
    public TryOnTaskItem(String taskId, OutfitPlanItem item, boolean selected, Integer stageSequence) {
        super("titem"); this.taskId = taskId; this.itemId = item.getItemId(); this.slot = item.getSlot();
        this.itemName = item.getItemName(); this.imageUrl = item.getImageUrl(); this.selected = selected;
        this.stageSequence = stageSequence; this.stageStatus = selected ? TryOnStageStatus.PENDING : TryOnStageStatus.SKIPPED;
    }
    public void start(String inputImageUrl, String provider, String resultKind) {
        this.stageStatus = TryOnStageStatus.SUBMITTING; this.inputImageUrl = inputImageUrl; this.startedAt = Instant.now();
        this.effectiveProvider = provider; this.resultKind = resultKind;
    }
    public void fallback(String provider, String reason) {
        this.stageStatus = TryOnStageStatus.SUBMITTING; this.effectiveProvider = provider;
        this.fallbackUsed = true; this.fallbackReason = reason; this.resultKind = "AI_PREVIEW";
        this.providerTaskId = null; this.providerStatus = "fallback";
    }
    public void submitted(String providerTaskId, String providerStatus) { this.providerTaskId = providerTaskId; this.providerStatus = providerStatus; this.stageStatus = TryOnStageStatus.PROCESSING; }
    public void processing(String providerStatus) { this.providerStatus = providerStatus; this.stageStatus = TryOnStageStatus.PROCESSING; }
    public void succeed(String resultImageUrl) { this.resultImageUrl = resultImageUrl; this.rendered = true; this.stageStatus = TryOnStageStatus.SUCCEEDED; this.completedAt = Instant.now(); }
    public void fail(String code, String message) { this.errorCode = code; this.errorMessage = message; this.stageStatus = TryOnStageStatus.FAILED; this.completedAt = Instant.now(); }
    public String getTaskId() { return taskId; }
    public String getItemId() { return itemId; }
    public String getSlot() { return slot; }
    public String getItemName() { return itemName; }
    public String getImageUrl() { return imageUrl; }
    public boolean isRendered() { return rendered; }
    public boolean isSelected() { return selected; }
    public Integer getStageSequence() { return stageSequence; }
    public TryOnStageStatus getStageStatus() { return stageStatus; }
    public String getProviderTaskId() { return providerTaskId; }
    public String getEffectiveProvider() { return effectiveProvider; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public String getFallbackReason() { return fallbackReason; }
    public String getResultKind() { return resultKind; }
    public String getProviderStatus() { return providerStatus; }
    public String getInputImageUrl() { return inputImageUrl; }
    public String getResultImageUrl() { return resultImageUrl; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
