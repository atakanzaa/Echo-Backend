-- Image-only post'larda caption/content opsiyonel olduğundan
-- community_posts.content sütununu nullable yapıyoruz
ALTER TABLE community_posts ALTER COLUMN content DROP NOT NULL;
