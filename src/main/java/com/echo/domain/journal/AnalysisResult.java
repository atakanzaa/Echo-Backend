package com.echo.domain.journal;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "mood_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal moodScore;

    @Column(name = "mood_label", nullable = false, length = 20)
    private String moodLabel;

    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> topics;

    @Column(name = "reflective_question", nullable = false, columnDefinition = "TEXT")
    private String reflectiveQuestion;

    @Column(name = "key_emotions", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> keyEmotions;

    @Column(name = "energy_level", nullable = false, length = 10)
    private String energyLevel;

    @Column(name = "raw_ai_response", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawAiResponse;

    @Column(name = "ai_provider", length = 20)
    private String aiProvider;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
