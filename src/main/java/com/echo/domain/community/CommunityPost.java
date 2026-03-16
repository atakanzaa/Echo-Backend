package com.echo.domain.community;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "community_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommunityPost {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(columnDefinition = "TEXT")
    private String content;
    @Column(name = "content_type", length = 20) @Builder.Default
    private String contentType = "text";
    @Column(name = "audio_url") private String audioUrl;
    @Column(name = "audio_duration") private Integer audioDuration;
    @Column(name = "image_url", length = 500) private String imageUrl;
    @Column(length = 10) private String emoji;
    @Column(name = "is_anonymous", nullable = false) @Builder.Default private boolean anonymous = false;
    @Column(name = "is_public", nullable = false) @Builder.Default private boolean publicPost = true;
    @Column(name = "likes_count", nullable = false) @Builder.Default private int likesCount = 0;
    @Column(name = "comments_count", nullable = false) @Builder.Default private int commentsCount = 0;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }
}
