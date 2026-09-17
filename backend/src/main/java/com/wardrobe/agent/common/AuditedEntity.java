package com.wardrobe.agent.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;


/** 所有 JPA 实体共享的业务 ID、创建时间和更新时间。 */
@MappedSuperclass
public abstract class AuditedEntity {
    @Id
    @Column(length = 40, nullable = false, updatable = false)
    private String id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    /** JPA 通过反射创建实体时需要无参构造器。 */
    protected AuditedEntity() {}

    /** 新业务实体通过前缀生成可读类型的随机 ID。 */
    protected AuditedEntity(String prefix) {
        this.id = PrefixIds.next(prefix);
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
