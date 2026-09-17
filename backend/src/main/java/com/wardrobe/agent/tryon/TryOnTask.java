package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tryon_task", uniqueConstraints = @UniqueConstraint(name = "uk_tryon_idempotency", columnNames = {"user_id", "idempotency_key"}))
/**
 * 试穿任务及其状态机数据；唯一幂等键防止重复任务，@Version 防止并发更新互相覆盖。
 */
public class TryOnTask extends AuditedEntity {
    @Column(name = "user_id", nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String planId;
    @Column(nullable = false) private int planVersion;
    @Column(nullable = false, length = 40) private String userModelId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(length = 30) private String effectiveProvider;
    @Column(nullable = false) private boolean fallbackUsed;
    @Column(length = 500) private String fallbackReason;
    @Column(nullable = false, length = 30) private String resultKind;
    @Column(length = 120) private String providerTaskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TryOnStatus status;
    @Column(length = 80) private String providerStatus;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(length = 40) private String retryFromTaskId;
    @Column(nullable = false) private int pollCount;
    private Instant nextPollAt;
    private Instant lastPolledAt;
    private Instant deadlineAt;
    @Column(length = 80) private String errorCode;
    @Column(length = 80) private String providerErrorCode;
    @Column(length = 500) private String errorMessage;
    @Column(length = 500) private String resultImageUrl;
    @Column(length = 500) private String providerResultUrl;
    private Instant resultExpiresAt;
    @Column(nullable = false, length = 30) private String tryOnCoverage;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json") private Set<String> selectedItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> renderedItemIds = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> unrenderedItemIds = new LinkedHashSet<>();
    @Column(nullable = false, length = 64) private String confirmationTokenHash;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false) private long statusVersion;
    @Column(length = 80) private String leaseOwner;
    private Instant leaseUntil;
    @Column(nullable = false) private int attemptCount;
    private Instant nextAttemptAt;
    @Column(nullable = false) private boolean simulateFailure;
    @Version private long rowVersion;

    protected TryOnTask() {}
    public TryOnTask(String userId, String planId, int planVersion, String modelId, String provider, String idempotencyKey,
                     String tokenHash, String fingerprint, String coverage, Set<String> selected, Set<String> unrendered,
                     boolean simulateFailure, String retryFromTaskId) {
        super("task"); this.userId = userId; this.planId = planId; this.planVersion = planVersion; this.userModelId = modelId;
        this.provider = provider; this.status = TryOnStatus.CREATED; this.idempotencyKey = idempotencyKey;
        this.effectiveProvider = provider; this.resultKind = TryOnResultKinds.forProvider(provider);
        this.confirmationTokenHash = tokenHash; this.requestFingerprint = fingerprint; this.tryOnCoverage = coverage;
        this.selectedItemIds = new LinkedHashSet<>(selected); this.renderedItemIds = new LinkedHashSet<>();
        this.unrenderedItemIds = new LinkedHashSet<>(unrendered);
        this.simulateFailure = simulateFailure; this.retryFromTaskId = retryFromTaskId; this.deadlineAt = Instant.now().plusSeconds(60);
    }
    /** 每次状态迁移递增 statusVersion，SSE 客户端可据此判断事件新旧。 */
    public void transition(TryOnStatus next) { this.status = next; this.statusVersion++; this.providerStatus = next.name(); }
    public void succeed(String resultUrl, String coverage) { transition(TryOnStatus.SUCCEEDED); this.resultImageUrl = resultUrl; this.tryOnCoverage = coverage; }
    public void stageSucceeded(String itemId, String resultUrl, String effectiveProvider, String resultKind,
                               boolean fallbackUsed, String fallbackReason) {
        this.renderedItemIds.add(itemId);
        this.unrenderedItemIds.remove(itemId);
        this.resultImageUrl = resultUrl;
        if (fallbackUsed || !this.fallbackUsed) this.effectiveProvider = effectiveProvider;
        if (fallbackUsed) {
            this.fallbackUsed = true;
            this.fallbackReason = fallbackReason;
            this.resultKind = "AI_PREVIEW";
        } else if (!this.fallbackUsed) {
            this.resultKind = resultKind;
        }
        transition(TryOnStatus.PROCESSING);
    }
    public void fallbackStarted(String provider, String reason) {
        this.effectiveProvider = provider; this.fallbackUsed = true; this.fallbackReason = reason;
        this.resultKind = "AI_PREVIEW"; transition(TryOnStatus.PROCESSING);
    }
    public void partial(String code, String message) { transition(TryOnStatus.PARTIALLY_SUCCEEDED); this.errorCode = code; this.errorMessage = message; }
    public void fail(String code, String message) { transition(TryOnStatus.FAILED); this.errorCode = code; this.errorMessage = message; }
    public void submissionUnknown(String message) { transition(TryOnStatus.SUBMISSION_UNKNOWN); this.errorCode = "SUBMISSION_UNKNOWN"; this.errorMessage = message; clearLease(); }
    public void renewLease(Instant until) { this.leaseUntil = until; }
    public void scheduleRetry(Instant nextAttemptAt, String code, String message) {
        transition(TryOnStatus.SUBMITTED); this.nextAttemptAt = nextAttemptAt; this.errorCode = code; this.errorMessage = message; clearLease();
    }
    public void clearLease() { this.leaseOwner = null; this.leaseUntil = null; this.nextAttemptAt = null; }
    public String getUserId() { return userId; }
    public String getPlanId() { return planId; }
    public int getPlanVersion() { return planVersion; }
    public String getUserModelId() { return userModelId; }
    public String getProvider() { return provider; }
    public String getEffectiveProvider() { return effectiveProvider; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public String getFallbackReason() { return fallbackReason; }
    public String getResultKind() { return resultKind; }
    public TryOnStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRetryFromTaskId() { return retryFromTaskId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getResultImageUrl() { return resultImageUrl; }
    public String getTryOnCoverage() { return tryOnCoverage; }
    public Set<String> getSelectedItemIds() { return selectedItemIds == null ? Set.of() : Set.copyOf(selectedItemIds); }
    public Set<String> getRenderedItemIds() { return renderedItemIds == null ? Set.of() : Set.copyOf(renderedItemIds); }
    public Set<String> getUnrenderedItemIds() { return unrenderedItemIds == null ? Set.of() : Set.copyOf(unrenderedItemIds); }
    public long getStatusVersion() { return statusVersion; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public boolean isSimulateFailure() { return simulateFailure; }
    public boolean hasRenderedItems() { return renderedItemIds != null && !renderedItemIds.isEmpty(); }
}
