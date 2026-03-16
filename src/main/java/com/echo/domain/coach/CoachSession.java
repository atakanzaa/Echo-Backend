package com.echo.domain.coach;

import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "coach_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CoachSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(length = 255) private String title;
    @CreationTimestamp @Column(name = "started_at", nullable = false, updatable = false) private OffsetDateTime startedAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "is_active", nullable = false) @Builder.Default private boolean active = true;

    /** Journal hakkında konuşma başlatıldığında ilgili entry — nullable */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;
}
