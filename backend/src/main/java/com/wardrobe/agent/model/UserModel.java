package com.wardrobe.agent.model;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_model")
/** 用户或系统预设的试穿模特实体；用户上传项需要记录授权状态。 */
public class UserModel extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 20) private String type;
    @Column(length = 40) private String fileId;
    @Column(nullable = false, length = 500) private String imageUrl;
    @Column(nullable = false) private boolean defaultModel;
    @Column(nullable = false) private boolean authorized;
    @Column(nullable = false) private boolean deleted;

    protected UserModel() {}
    public UserModel(String userId, String name, String type, String fileId, String imageUrl, boolean authorized) {
        super("model"); this.userId = userId; this.name = name; this.type = type; this.fileId = fileId;
        this.imageUrl = imageUrl; this.authorized = authorized;
    }
    public void setDefaultModel(boolean value) { this.defaultModel = value; }
    public void markDeleted() { this.deleted = true; this.defaultModel = false; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getFileId() { return fileId; }
    public String getImageUrl() { return imageUrl; }
    public boolean isDefaultModel() { return defaultModel; }
    public boolean isAuthorized() { return authorized; }
    public boolean isDeleted() { return deleted; }
}
