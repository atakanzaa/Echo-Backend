package com.echo.repository;

import com.echo.domain.community.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {
    Page<CommunityPost> findByPublicPostTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<CommunityPost> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Page<CommunityPost> findByPublicPostTrueAndUserIdInOrderByCreatedAtDesc(List<UUID> userIds, Pageable pageable);
}
