package com.wardrobe.agent.wardrobe;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.media.MediaStorageService;
import com.wardrobe.agent.retrieval.WardrobeEmbeddingIndexer;
import com.wardrobe.agent.user.AppUser;
import com.wardrobe.agent.user.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
/** 管理真实衣物及其 Embedding 生命周期；MySQL 业务数据始终是权限和状态的事实来源。 */
public class WardrobeService {
    private final WardrobeItemRepository items;
    private final AppUserRepository users;
    private final MediaStorageService media;
    private final WardrobeEmbeddingIndexer embeddings;

    public WardrobeService(WardrobeItemRepository items, AppUserRepository users, MediaStorageService media,
                           WardrobeEmbeddingIndexer embeddings) {
        this.items = items;
        this.users = users;
        this.media = media;
        this.embeddings = embeddings;
    }

    @Transactional(readOnly = true)
    /** 使用 JPA Specification 动态组合可选筛选条件。 */
    public List<WardrobeItemView> list(String userId, String category, String season, String style) {
        Specification<WardrobeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (category != null && !category.isBlank()) predicates.add(cb.equal(root.get("category"), category));
            if (season != null && !season.isBlank()) predicates.add(cb.like(root.get("seasonTags"), "%\"" + season + "\"%"));
            if (style != null && !style.isBlank()) predicates.add(cb.like(root.get("styleTags"), "%\"" + style + "\"%"));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return items.findAll(spec, Sort.by("createdAt").ascending()).stream().map(WardrobeItemView::from).toList();
    }

    @Transactional(readOnly = true)
    public WardrobeItemView get(String userId, String id) { return WardrobeItemView.from(require(userId, id)); }

    @Transactional
    /** 创建衣物前校验图片所有权，落库后尽力生成最新 Embedding。 */
    public WardrobeItemView create(String userId, WardrobeItemCommand command) {
        if (command.imageFileId() == null || command.imageFileId().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "请先上传衣物图片");
        }
        media.requireOwned(command.imageFileId(), userId);
        WardrobeItem item = new WardrobeItem(userId, command, "/api/files/" + command.imageFileId(), "UPLOAD", command.imageFileId());
        incrementRevision(userId);
        WardrobeItem saved = items.saveAndFlush(item);
        embeddings.indexBestEffort(saved);
        return WardrobeItemView.from(saved);
    }

    @Transactional
    /** JPA 脏检查保存字段变化，flush 后再按新内容更新向量索引。 */
    public WardrobeItemView update(String userId, String id, WardrobeItemCommand command) {
        WardrobeItem item = require(userId, id);
        item.update(command);
        incrementRevision(userId);
        items.flush();
        embeddings.indexBestEffort(item);
        return WardrobeItemView.from(item);
    }

    @Transactional
    /** 业务数据使用软删除，同时移除向量，避免已删除衣物继续被召回。 */
    public void delete(String userId, String id) {
        WardrobeItem item = require(userId, id);
        item.markDeleted();
        embeddings.delete(userId, id);
        incrementRevision(userId);
    }

    /** 所有单件查询同时校验 userId 和 deleted，集中防止水平越权。 */
    public WardrobeItem require(String userId, String id) {
        return items.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "衣物不存在"));
    }

    /** 衣橱修订号变化后，旧搭配可识别自己基于哪个衣橱版本生成。 */
    private void incrementRevision(String userId) {
        AppUser user = users.findById(userId).orElseThrow();
        user.incrementWardrobeRevision();
    }
}
