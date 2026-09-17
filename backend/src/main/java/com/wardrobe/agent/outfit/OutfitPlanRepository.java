package com.wardrobe.agent.outfit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** 搭配方案仓库，查询方法均包含 userId 作为租户边界。 */
public interface OutfitPlanRepository extends JpaRepository<OutfitPlan, String> {
    Optional<OutfitPlan> findByIdAndUserId(String id, String userId);
    List<OutfitPlan> findAllByUserIdOrderByCreatedAtDesc(String userId);
    List<OutfitPlan> findAllByConversationIdAndUserIdOrderByCreatedAtAsc(String conversationId, String userId);
}
