package com.echo.domain.goal;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * AI'nın günlük girişlerinden tespit ettiği kullanıcı hedefleri.
 * Kullanıcı durumu manuel olarak güncelleyebilir: PENDING → COMPLETED | DISMISSED
 * sourceJournalEntryId: hangi günlük girişinden tespit edildiğini gösterir (nullable).
 */
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

    /**
     * Hedef durumu: PENDING | ACTIVE | COMPLETED | DISMISSED
     * Yeni tespitler PENDING olarak başlar.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * Hedefin tespit edildiği günlük girişi ID'si.
     * Plain UUID — circular dependency önlemek için @ManyToOne kullanılmadı.
     * DB seviyesinde FK constraint V12 migration'da tanımlı.
     */
    @Column(name = "source_journal_entry_id")
    private UUID sourceJournalEntryId;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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
