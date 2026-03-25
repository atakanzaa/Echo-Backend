package com.echo.domain.journal;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "audio_duration_seconds")
    private Integer audioDurationSeconds;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Convert(converter = EntryStatusConverter.class)
    @Column(nullable = false)
    @Builder.Default
    private EntryStatus status = EntryStatus.UPLOADING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Client-generated UUID — prevents duplicate submissions on retry / bad network
    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
