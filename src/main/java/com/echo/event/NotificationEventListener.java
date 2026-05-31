package com.echo.event;

import com.echo.domain.notification.NotificationType;
import com.echo.service.NotificationService;
import com.echo.service.notification.NotificationTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final String TARGET_JOURNAL_ENTRY = "JOURNAL_ENTRY";
    private static final String TARGET_ACHIEVEMENT = "ACHIEVEMENT";
    private static final String TARGET_POST = "POST";

    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJournalAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        notificationService.notifyTemplated(
                event.userId(),
                NotificationType.ANALYSIS_COMPLETE,
                Map.of(),
                TARGET_JOURNAL_ENTRY,
                event.journalEntryId(),
                "analysis_complete:" + event.journalEntryId(),
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAchievementEarned(AchievementEarnedEvent event) {
        notificationService.notifyTemplated(
                event.userId(),
                NotificationType.ACHIEVEMENT_EARNED,
                templateService.achievementVars(event.badgeTitle(), event.badgeEmoji()),
                TARGET_ACHIEVEMENT,
                null,
                "achievement_earned:" + event.userId() + ":" + event.badgeKey(),
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAchievementProgress(AchievementProgressEvent event) {
        notificationService.notifyTemplated(
                event.userId(),
                NotificationType.ACHIEVEMENT_PROGRESS,
                templateService.achievementProgressVars(event.badgeTitle(), event.remaining()),
                TARGET_ACHIEVEMENT,
                null,
                "achievement_progress:" + event.userId() + ":" + event.badgeKey() + ":80",
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommented(PostCommentedEvent event) {
        if (event.actorUserId().equals(event.postOwnerUserId())) {
            return;
        }
        notificationService.notifyTemplated(
                event.postOwnerUserId(),
                NotificationType.POST_COMMENTED,
                templateService.commentVars(event.anonymousPost()),
                TARGET_POST,
                event.postId(),
                "post_commented:" + event.commentId(),
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        if (event.actorUserId().equals(event.postOwnerUserId())) {
            return;
        }
        notificationService.notifyTemplated(
                event.postOwnerUserId(),
                NotificationType.POST_LIKED,
                Map.of(),
                TARGET_POST,
                event.postId(),
                "post_liked:" + event.postId() + ":" + event.actorUserId(),
                "post_like:" + event.postId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentReplied(CommentRepliedEvent event) {
        if (event.actorUserId().equals(event.commentOwnerUserId())) {
            return;
        }
        notificationService.notifyTemplated(
                event.commentOwnerUserId(),
                NotificationType.COMMENT_REPLIED,
                templateService.commentVars(event.anonymousPost()),
                TARGET_POST,
                event.postId(),
                "comment_replied:" + event.commentId(),
                null
        );
    }
}
