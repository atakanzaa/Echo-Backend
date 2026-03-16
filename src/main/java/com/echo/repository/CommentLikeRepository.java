package com.echo.repository;

import com.echo.domain.community.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {
    Optional<CommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);
    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);
}
