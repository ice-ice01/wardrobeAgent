package com.wardrobe.agent.tryon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.Collection;

/** 试穿任务仓库，包含所有权、幂等键和用户任务列表查询。 */
public interface TryOnTaskRepository extends JpaRepository<TryOnTask, String> {
    Optional<TryOnTask> findByIdAndUserId(String id, String userId);
    Optional<TryOnTask> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    List<TryOnTask> findAllByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndProviderAndCreatedAtAfter(String userId, String provider, Instant createdAt);
    long countByUserIdAndProviderAndStatusIn(String userId, String provider, Collection<TryOnStatus> statuses);
    long countByProviderAndStatusIn(String provider, Collection<TryOnStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TryOnTask task
               set task.leaseOwner = :owner,
                   task.leaseUntil = :leaseUntil,
                   task.attemptCount = task.attemptCount + 1,
                   task.nextAttemptAt = null
             where task.id = :taskId
               and task.status in :statuses
               and (task.nextAttemptAt is null or task.nextAttemptAt <= :now)
               and (task.leaseUntil is null or task.leaseUntil < :now)
            """)
    int claim(@Param("taskId") String taskId, @Param("owner") String owner,
              @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil,
              @Param("statuses") Collection<TryOnStatus> statuses);

    @Query("""
            select task.id from TryOnTask task
             where task.status in :statuses
               and (task.nextAttemptAt is null or task.nextAttemptAt <= :now)
               and (task.leaseUntil is null or task.leaseUntil < :now)
             order by task.createdAt asc
            """)
    List<String> findRecoverableIds(@Param("statuses") Collection<TryOnStatus> statuses,
                                    @Param("now") Instant now,
                                    org.springframework.data.domain.Pageable pageable);
}
