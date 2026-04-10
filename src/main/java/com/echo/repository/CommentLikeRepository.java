package com.echo.repository;

import com.echo.domain.community.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, UUID> {
    Optional<CommentLike> findByCommentIdAndUserId(UUID commentId, UUID userId);
    boolean existsByCommentIdAndUserId(UUID commentId, UUID userId);

    @Query("SELECT cl.comment.id FROM CommentLike cl WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    Set<UUID> findCommentIdsLikedByUser(@Param("userId") UUID userId, @Param("commentIds") List<UUID> commentIds);
}
