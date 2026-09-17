package com.wardrobe.agent.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/** 消息仓库；方法名查询由 Spring Data 自动实现，最近窗口使用自定义 JPQL。 */
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findAllByConversationIdAndUserIdOrderByCreatedAtAsc(String conversationId, String userId);
    /** 只读取可进入模型上下文的已完成消息，并通过 Pageable 限制数量。 */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.userId = :userId
              and m.status = 'COMPLETED' and m.content <> ''
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findRecentCompleted(@Param("conversationId") String conversationId,
                                      @Param("userId") String userId,
                                      Pageable pageable);
    Optional<Message> findByUserIdAndClientMessageIdAndRole(String userId, String clientMessageId, String role);
}
