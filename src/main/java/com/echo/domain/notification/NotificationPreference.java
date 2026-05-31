package com.echo.domain.notification;

import com.echo.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "master_enabled", nullable = false)
    @Builder.Default
    private boolean masterEnabled = true;

    @Column(name = "daily_reminders_enabled", nullable = false)
    @Builder.Default
    private boolean dailyRemindersEnabled = true;

    @Column(name = "community_enabled", nullable = false)
    @Builder.Default
    private boolean communityEnabled = true;

    @Column(name = "achievements_enabled", nullable = false)
    @Builder.Default
    private boolean achievementsEnabled = true;

    @Column(name = "insights_enabled", nullable = false)
    @Builder.Default
    private boolean insightsEnabled = true;

    @Column(name = "mood_alerts_enabled", nullable = false)
    @Builder.Default
    private boolean moodAlertsEnabled = true;

    @Column(name = "capsules_enabled", nullable = false)
    @Builder.Default
    private boolean capsulesEnabled = true;

    @Column(name = "coach_enabled", nullable = false)
    @Builder.Default
    private boolean coachEnabled = true;

    @Column(name = "system_enabled", nullable = false)
    @Builder.Default
    private boolean systemEnabled = true;

    /** Preferred local hour [0,23] for daily reminders. */
    @Column(name = "preferred_local_hour", nullable = false)
    @Builder.Default
    private int preferredLocalHour = 20;

    /** Quiet hours window — both columns interpreted in the user's timezone.
     *  null means quiet hours disabled. Range can wrap midnight (e.g. 22->7). */
    @Column(name = "quiet_hours_start_local")
    private Integer quietHoursStartLocal;

    @Column(name = "quiet_hours_end_local")
    private Integer quietHoursEndLocal;

    /** IANA timezone identifier; falls back to the user's timezone if blank. */
    @Column(length = 64)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public boolean isCategoryEnabled(NotificationCategory category) {
        if (!masterEnabled) return false;
        return switch (category) {
            case DAILY_REMINDERS -> dailyRemindersEnabled;
            case COMMUNITY -> communityEnabled;
            case ACHIEVEMENTS -> achievementsEnabled;
            case INSIGHTS -> insightsEnabled;
            case MOOD_ALERTS -> moodAlertsEnabled;
            case CAPSULES -> capsulesEnabled;
            case COACH -> coachEnabled;
            case SYSTEM -> systemEnabled;
        };
    }
}
