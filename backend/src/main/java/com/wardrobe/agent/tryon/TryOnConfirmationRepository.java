package com.wardrobe.agent.tryon;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** 根据令牌哈希读取一次性确认记录。 */
public interface TryOnConfirmationRepository extends JpaRepository<TryOnConfirmation, String> {
    Optional<TryOnConfirmation> findByTokenHash(String tokenHash);
}
