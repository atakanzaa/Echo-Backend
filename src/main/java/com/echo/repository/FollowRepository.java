package com.echo.repository;

import com.echo.domain.community.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);
    List<Follow> findByFollowerId(UUID followerId);

    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId")
    List<UUID> findFollowingIdsByFollowerId(@Param("followerId") UUID followerId);
}
