package com.wardrobe.agent.tryon;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tryon_confirmation")
/** 一次性试穿确认实体；只保存令牌哈希，不保存可直接使用的明文令牌。 */
public class TryOnConfirmation extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 40) private String planId;
    @Column(nullable = false) private int planVersion;
    @Column(nullable = false, length = 40) private String userModelId;
    @Column(nullable = false, length = 30) private String provider;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "json")
    private Set<String> selectedItemIds = new LinkedHashSet<>();
    @Column(nullable = false, length = 64, unique = true) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;

    protected TryOnConfirmation() {}
    public TryOnConfirmation(String userId, String planId, int planVersion, String modelId, String provider,
                             Set<String> selectedItemIds, String tokenHash, Instant expiresAt) {
        super("confirm"); this.userId = userId; this.planId = planId; this.planVersion = planVersion;
        this.userModelId = modelId; this.provider = provider; this.selectedItemIds = new LinkedHashSet<>(selectedItemIds);
        this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
    public void consume() { this.consumedAt = Instant.now(); }
    public String getUserId() { return userId; }
    public String getPlanId() { return planId; }
    public int getPlanVersion() { return planVersion; }
    public String getUserModelId() { return userModelId; }
    public String getProvider() { return provider; }
    public Set<String> getSelectedItemIds() { return selectedItemIds == null ? Set.of() : Set.copyOf(selectedItemIds); }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
