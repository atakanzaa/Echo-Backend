package com.echo.event;

import com.echo.domain.notification.NotificationType;
import com.echo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJournalAnalysisCompleted(JournalAnalysisCompletedEvent event) {
        notificationService.notify(
                event.userId(),
                NotificationType.ANALYSIS_COMPLETE,
                "Analysis Complete",
                "Your journal analysis is ready.",
                "JOURNAL_ENTRY",
                event.journalEntryId(),
                "analysis_complete:" + event.journalEntryId(),
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAchievementEarned(AchievementEarnedEvent event) {
        notificationService.notify(
                event.userId(),
                NotificationType.ACHIEVEMENT_EARNED,
                "Achievement Unlocked",
                "You earned " + event.badgeTitle() + " " + event.badgeEmoji(),
                "ACHIEVEMENT",
                null,
                "achievement_earned:" + event.userId() + ":" + event.badgeKey(),
                null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommented(PostCommentedEvent event) {
        if (event.actorUserId().equals(event.postOwnerUserId())) {
            return;
        }
        notificationService.notify(
                event.postOwnerUserId(),
                NotificationType.POST_COMMENTED,
                "New Comment",
                event.anonymousPost() ? "Someone commented on your post." : "New comment on your post.",
                "POST",
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
        notificationService.notify(
                event.postOwnerUserId(),
                NotificationType.POST_LIKED,
                "New Like",
                "1 person liked your post",
                "POST",
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
        notificationService.notify(
                event.commentOwnerUserId(),
                NotificationType.COMMENT_REPLIED,
                "New Reply",
                event.anonymousPost() ? "Someone replied to your comment." : "You have a new reply.",
                "POST",
                event.postId(),
                "comment_replied:" + event.commentId(),
                null
        );
    }
}
