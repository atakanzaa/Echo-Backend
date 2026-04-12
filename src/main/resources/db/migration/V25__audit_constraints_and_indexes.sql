-- V25: Production audit fixes — unique constraints, missing indexes, CHECK constraints

-- =====================================================
-- 1. UNIQUE CONSTRAINTS (prevent race-condition duplicates)
-- =====================================================

-- CommentLike: prevent duplicate likes on same comment by same user
ALTER TABLE comment_likes
    ADD CONSTRAINT uk_comment_likes_comment_user UNIQUE (comment_id, user_id);

-- Follow: prevent duplicate follows
ALTER TABLE follows
    ADD CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id);

-- =====================================================
-- 2. CHECK CONSTRAINTS (counter safety)
-- =====================================================

ALTER TABLE community_posts
    ADD CONSTRAINT chk_posts_likes_count_non_negative CHECK (likes_count >= 0);

ALTER TABLE community_posts
    ADD CONSTRAINT chk_posts_comments_count_non_negative CHECK (comments_count >= 0);

ALTER TABLE post_comments
    ADD CONSTRAINT chk_comments_likes_count_non_negative CHECK (likes_count >= 0);

-- =====================================================
-- 3. MISSING INDEXES (query performance)
-- =====================================================

-- coach_messages: ordered retrieval per session (used on every sendMessage + getMessages)
CREATE INDEX IF NOT EXISTS idx_coach_messages_session_created
    ON coach_messages (session_id, created_at ASC);

-- coach_sessions: session listing per user
CREATE INDEX IF NOT EXISTS idx_coach_sessions_user_updated
    ON coach_sessions (user_id, updated_at DESC);

-- notifications: unread count + listing
CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created
    ON notifications (user_id, is_read, created_at DESC);

-- community_posts: global feed (public posts by recency)
CREATE INDEX IF NOT EXISTS idx_community_posts_public_created
    ON community_posts (created_at DESC) WHERE is_public = true;

-- post_comments: comment listing per post (top-level + replies)
CREATE INDEX IF NOT EXISTS idx_post_comments_post_parent_created
    ON post_comments (post_id, parent_id, created_at ASC);

-- coach_messages: session message count for quota check
CREATE INDEX IF NOT EXISTS idx_coach_messages_session_role
    ON coach_messages (session_id, role);
