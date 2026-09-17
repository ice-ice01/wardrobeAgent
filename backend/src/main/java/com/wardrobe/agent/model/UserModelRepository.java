package com.wardrobe.agent.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** 用户形象仓库，所有查询以 userId 和软删除状态隔离。 */
public interface UserModelRepository extends JpaRepository<UserModel, String> {
    List<UserModel> findAllByUserIdAndDeletedFalseOrderByCreatedAtAsc(String userId);
    Optional<UserModel> findByIdAndUserIdAndDeletedFalse(String id, String userId);
}
