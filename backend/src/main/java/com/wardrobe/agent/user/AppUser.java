package com.wardrobe.agent.user;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
/** 应用用户实体；wardrobeRevision 在衣橱变化时递增，用于标记搭配输入版本。 */
public class AppUser extends AuditedEntity {
    @Column(nullable = false, unique = true, length = 80)
    private String username;
    @Column(nullable = false, length = 120)
    private String passwordHash;
    @Column(nullable = false, length = 80)
    private String displayName;
    @Column(nullable = false)
    private long wardrobeRevision;
    @Column(nullable = false)
    private boolean enabled;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String displayName) {
        super("usr");
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;
    }

    public void incrementWardrobeRevision() { wardrobeRevision++; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public long getWardrobeRevision() { return wardrobeRevision; }
    public boolean isEnabled() { return enabled; }
}
