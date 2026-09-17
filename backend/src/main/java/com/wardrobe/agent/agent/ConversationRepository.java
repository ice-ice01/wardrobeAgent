package com.wardrobe.agent.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** 会话仓库；详情查询同时携带 userId，防止越权访问其他用户的会话。 */
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    Optional<Conversation> findByIdAndUserId(String id, String userId);
    List<Conversation> findAllByUserIdOrderByLastMessageAtDesc(String userId);
}
