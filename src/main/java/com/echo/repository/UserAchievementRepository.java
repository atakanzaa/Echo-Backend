package com.echo.repository;

import com.echo.domain.achievement.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUserId(UUID userId);
    boolean existsByUserIdAndBadgeKey(UUID userId, String badgeKey);
}
