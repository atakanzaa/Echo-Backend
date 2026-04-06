package com.echo.repository;

import com.echo.domain.community.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    Page<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);
    Page<PostComment> findByPostIdAndParentIsNullOrderByCreatedAtAsc(UUID postId, Pageable pageable);
    List<PostComment> findByParentIdOrderByCreatedAtAsc(UUID parentId);
    long countByParentId(UUID parentId);

    @Modifying
    @Query("UPDATE PostComment c SET c.likesCount = c.likesCount + 1 WHERE c.id = :id")
    void incrementLikesCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PostComment c SET c.likesCount = GREATEST(0, c.likesCount - 1) WHERE c.id = :id")
    void decrementLikesCount(@Param("id") UUID id);
}
