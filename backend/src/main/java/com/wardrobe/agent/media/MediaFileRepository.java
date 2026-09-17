package com.wardrobe.agent.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 媒体元数据仓库；读取时必须同时匹配 ownerId 和 READY 状态。 */
public interface MediaFileRepository extends JpaRepository<MediaFile, String> {
    Optional<MediaFile> findByIdAndOwnerIdAndStatus(String id, String ownerId, String status);
}
