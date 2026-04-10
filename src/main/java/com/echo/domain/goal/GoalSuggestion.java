package com.echo.domain.goal;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "goal_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(name = "suggestion_type", nullable = false, length = 30)
    private String suggestionType;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_journal_entry_id")
    private UUID sourceJournalEntryId;

    @Column(name = "source_coach_session_id")
    private UUID sourceCoachSessionId;

    @Column(name = "source_coach_message_id")
    private UUID sourceCoachMessageId;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String timeframe;

    @Column(name = "goal_type", length = 50)
    private String goalType;

    @Column(name = "detected_text", columnDefinition = "TEXT")
    private String detectedText;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 120)
    private String dedupeKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
