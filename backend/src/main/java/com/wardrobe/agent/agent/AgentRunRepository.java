package com.wardrobe.agent.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    Optional<AgentRun> findByIdAndUserId(String id, String userId);
    Optional<AgentRun> findByUserIdAndClientMessageId(String userId, String clientMessageId);
    List<AgentRun> findAllByConversationIdAndUserIdOrderByCreatedAtDesc(String conversationId, String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentRun run
               set run.status = com.wardrobe.agent.agent.AgentRunStatus.RUNNING,
                   run.leaseOwner = :owner,
                   run.leaseUntil = :leaseUntil,
                   run.attemptCount = run.attemptCount + 1,
                   run.statusVersion = run.statusVersion + 1,
                   run.nextAttemptAt = null
             where run.id = :runId
               and run.status in :statuses
               and (run.nextAttemptAt is null or run.nextAttemptAt <= :now)
               and (run.leaseUntil is null or run.leaseUntil < :now)
            """)
    int claim(@Param("runId") String runId, @Param("owner") String owner,
              @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil,
              @Param("statuses") Collection<AgentRunStatus> statuses);

    @Query("""
            select run.id from AgentRun run
             where run.status in :statuses
               and (run.nextAttemptAt is null or run.nextAttemptAt <= :now)
               and (run.leaseUntil is null or run.leaseUntil < :now)
             order by run.createdAt asc
            """)
    List<String> findRecoverableIds(@Param("statuses") Collection<AgentRunStatus> statuses,
                                    @Param("now") Instant now, Pageable pageable);
}
