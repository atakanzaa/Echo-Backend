-- 1. Image desteği
ALTER TABLE community_posts ADD COLUMN image_url VARCHAR(500);

-- 2. Nested reply + comment like desteği
ALTER TABLE post_comments ADD COLUMN parent_id UUID REFERENCES post_comments(id) ON DELETE CASCADE;
ALTER TABLE post_comments ADD COLUMN likes_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_post_comments_parent ON post_comments(parent_id);

-- 3. Follow sistemi
CREATE TABLE follows (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    follower_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    following_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_follow UNIQUE (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id)
);

CREATE INDEX idx_follows_follower  ON follows(follower_id);
CREATE INDEX idx_follows_following ON follows(following_id);

-- 4. Comment like sistemi
CREATE TABLE comment_likes (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id UUID        NOT NULL REFERENCES post_comments(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_comment_like UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_likes_comment ON comment_likes(comment_id);
