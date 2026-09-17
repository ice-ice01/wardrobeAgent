package com.wardrobe.agent.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户账户仓库。 */
public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByUsername(String username);
}
