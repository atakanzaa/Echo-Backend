package com.echo.repository;

import com.echo.domain.community.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {
    Page<CommunityPost> findByPublicPostTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<CommunityPost> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<CommunityPost> findByPublicPostTrueAndUserIdInOrderByCreatedAtDesc(List<UUID> userIds, Pageable pageable);

    @Modifying
    @Query("UPDATE CommunityPost p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikesCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommunityPost p SET p.likesCount = GREATEST(0, p.likesCount - 1) WHERE p.id = :id")
    void decrementLikesCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentsCount = p.commentsCount + 1 WHERE p.id = :id")
    void incrementCommentsCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommunityPost p SET p.commentsCount = GREATEST(0, p.commentsCount - :count) WHERE p.id = :id")
    void decrementCommentsCount(@Param("id") UUID id, @Param("count") int count);
}
