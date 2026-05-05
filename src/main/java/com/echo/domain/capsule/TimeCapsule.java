package com.echo.domain.capsule;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_capsules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimeCapsule {
    public static final String CONTENT_TYPE_TEXT = "text";

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(length = 255) private String title;
    @Column(name = "content_text", columnDefinition = "TEXT") private String contentText;
    @Column(name = "content_type", length = 20) @Builder.Default private String contentType = CONTENT_TYPE_TEXT;
    @Column(name = "audio_url") private String audioUrl;
    @Column(name = "audio_duration") private Integer audioDuration;
    @Column(name = "source_journal_entry_id") private UUID sourceJournalEntryId;
    @Column(name = "sealed_at", nullable = false) private OffsetDateTime sealedAt;
    @Column(name = "unlock_at", nullable = false) private OffsetDateTime unlockAt;
    @Column(name = "opened_at") private OffsetDateTime openedAt;

    @Convert(converter = CapsuleStatusConverter.class)
    @Column(nullable = false)
    @Builder.Default
    private CapsuleStatus status = CapsuleStatus.SEALED;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public boolean isUnlocked() { return OffsetDateTime.now().isAfter(unlockAt); }
}
