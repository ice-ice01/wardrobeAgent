package com.wardrobe.agent.tryon;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 试穿任务商品快照仓库。 */
public interface TryOnTaskItemRepository extends JpaRepository<TryOnTaskItem, String> {
    List<TryOnTaskItem> findAllByTaskIdOrderByStageSequenceAsc(String taskId);
}
