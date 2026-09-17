package com.wardrobe.agent.wardrobe;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "wardrobe_item")
/** 真实衣物 JPA 实体；文本属性既用于页面筛选，也会被拼成 Embedding 文档和 AI 候选上下文。 */
public class WardrobeItem extends AuditedEntity {
    @Column(nullable = false, length = 40) private String userId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 30) private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private WardrobeSlot slot;
    @Column(nullable = false, length = 40) private String color;
    @Column(nullable = false, length = 60) private String material;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> seasonTags = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> sceneTags = new LinkedHashSet<>();
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "json") private Set<String> styleTags = new LinkedHashSet<>();
    @Column(nullable = false, length = 500) private String imageUrl;
    @Column(nullable = false, length = 20) private String imageSource;
    @Column(length = 40) private String originalFileId;
    @Column(length = 40) private String processedFileId;
    @Column(nullable = false, length = 20) private String imageStatus;
    @Column(length = 300) private String imageValidationMessage;
    private Integer warmthLevel;
    private Integer breathabilityLevel;
    @Column(nullable = false) private boolean deleted;

    /** JPA 专用无参构造器。 */
    protected WardrobeItem() {}

    public WardrobeItem(String userId, WardrobeItemCommand command, String imageUrl, String imageSource, String fileId) {
        super("item");
        this.userId = userId;
        apply(command);
        this.imageUrl = imageUrl;
        this.imageSource = imageSource;
        this.originalFileId = fileId;
        this.processedFileId = fileId;
        this.imageStatus = "READY";
    }

    /** 统一复用字段规范化逻辑，Service 会在此后刷新对应向量。 */
    public void update(WardrobeItemCommand command) { apply(command); }

    /** 软删除保留历史引用，查询和检索层必须显式排除 deleted=true。 */
    public void markDeleted() { this.deleted = true; }

    private void apply(WardrobeItemCommand command) {
        this.name = command.name().trim();
        this.category = command.category().trim();
        this.slot = command.slot();
        this.color = normalized(command.color());
        this.material = normalized(command.material());
        this.seasonTags = copy(command.seasonTags());
        this.sceneTags = copy(command.sceneTags());
        this.styleTags = copy(command.styleTags());
        this.warmthLevel = command.warmthLevel();
        this.breathabilityLevel = command.breathabilityLevel();
    }

    private static String normalized(String value) { return value == null || value.isBlank() ? "UNKNOWN" : value.trim(); }
    /** 防御性复制可变集合，避免外部修改绕过实体方法。 */
    private static Set<String> copy(Set<String> values) { return values == null ? new LinkedHashSet<>() : new LinkedHashSet<>(values); }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public WardrobeSlot getSlot() { return slot; }
    public String getColor() { return color; }
    public String getMaterial() { return material; }
    public Set<String> getSeasonTags() { return Set.copyOf(seasonTags); }
    public Set<String> getSceneTags() { return Set.copyOf(sceneTags); }
    public Set<String> getStyleTags() { return Set.copyOf(styleTags); }
    public String getImageUrl() { return imageUrl; }
    public String getImageSource() { return imageSource; }
    public String getOriginalFileId() { return originalFileId; }
    public String getProcessedFileId() { return processedFileId; }
    public String getImageStatus() { return imageStatus; }
    public Integer getWarmthLevel() { return warmthLevel; }
    public Integer getBreathabilityLevel() { return breathabilityLevel; }
    public boolean isDeleted() { return deleted; }
}
