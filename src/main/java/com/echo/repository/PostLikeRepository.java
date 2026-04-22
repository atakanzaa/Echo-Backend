package com.echo.repository;

import com.echo.domain.community.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.id = :userId")
    Set<UUID> findLikedPostIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.id = :userId AND pl.post.id IN :postIds")
    Set<UUID> findLikedPostIdsByUserIdAndPostIds(@Param("userId") UUID userId,
                                                 @Param("postIds") Set<UUID> postIds);
}
