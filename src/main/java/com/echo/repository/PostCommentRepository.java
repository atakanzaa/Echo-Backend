package com.echo.repository;

import com.echo.domain.community.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    Page<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId, Pageable pageable);
    Page<PostComment> findByPostIdAndParentIsNullOrderByCreatedAtAsc(UUID postId, Pageable pageable);
    List<PostComment> findByParentIdOrderByCreatedAtAsc(UUID parentId);
    long countByParentId(UUID parentId);
}
