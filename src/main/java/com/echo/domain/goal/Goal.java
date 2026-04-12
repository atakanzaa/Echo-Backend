package com.echo.domain.goal;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String timeframe;

    @Column(name = "goal_type", length = 50)
    @Builder.Default
    private String goalType = "general";

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_type", nullable = false, length = 20)
    @Builder.Default
    private GoalCreationType creationType = GoalCreationType.AI;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoalStatus status = GoalStatus.PENDING;

    @Column(name = "source_journal_entry_id")
    private UUID sourceJournalEntryId;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_type", length = 20)
    private GoalCompletionType completionType;

    @Column(name = "completed_source_type", length = 20)
    private String completedSourceType;

    @Column(name = "completed_source_ref_id")
    private UUID completedSourceRefId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
