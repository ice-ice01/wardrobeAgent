package com.wardrobe.agent.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 推荐运行审计记录仓库。 */
public interface RecommendationRunRepository extends JpaRepository<RecommendationRun, String> {
    List<RecommendationRun> findAllByConversationIdAndUserIdOrderByCreatedAtAsc(String conversationId, String userId);
}
