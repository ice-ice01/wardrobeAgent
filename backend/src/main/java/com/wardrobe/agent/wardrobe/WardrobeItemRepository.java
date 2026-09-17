package com.wardrobe.agent.wardrobe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** 衣物仓库；JpaSpecificationExecutor 支持列表接口的动态过滤条件。 */
public interface WardrobeItemRepository extends JpaRepository<WardrobeItem, String>, JpaSpecificationExecutor<WardrobeItem> {
    Optional<WardrobeItem> findByIdAndUserIdAndDeletedFalse(String id, String userId);
    List<WardrobeItem> findAllByUserIdOrderByCreatedAtAsc(String userId);
    List<WardrobeItem> findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(String userId);
    long countByUserIdAndDeletedFalse(String userId);
}
