package com.wardrobe.agent.media;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "media_file")
/** 媒体元数据实体；二进制保存在受控目录，数据库只保存相对路径和校验信息。 */
public class MediaFile extends AuditedEntity {
    @Column(nullable = false, length = 40)
    private String ownerId;
    @Column(nullable = false, length = 30)
    private String purpose;
    @Column(nullable = false, length = 80)
    private String mimeType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 500)
    private String storagePath;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(nullable = false, length = 20)
    private String status;
    private Instant deletedAt;
    private Instant expiresAt;

    protected MediaFile() {}

    public MediaFile(String ownerId, String purpose, String mimeType, long sizeBytes, String storagePath, String sha256) {
        super("file");
        this.ownerId = ownerId;
        this.purpose = purpose;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.sha256 = sha256;
        this.status = "READY";
    }

    public void markDeleted() { this.status = "DELETED"; this.deletedAt = Instant.now(); }
    public String getOwnerId() { return ownerId; }
    public String getPurpose() { return purpose; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public String getSha256() { return sha256; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
