package com.echo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounterReconciliationJob {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void reconcileCommunityCounters() {
        int likesUpdated = jdbcTemplate.update("""
                UPDATE community_posts p
                SET likes_count = (
                    SELECT COUNT(*)
                    FROM post_likes pl
                    WHERE pl.post_id = p.id
                )
                """);

        int commentsUpdated = jdbcTemplate.update("""
                UPDATE community_posts p
                SET comments_count = (
                    SELECT COUNT(*)
                    FROM post_comments pc
                    WHERE pc.post_id = p.id
                )
                """);

        log.info("Community counter reconciliation complete: likesUpdated={}, commentsUpdated={}",
                likesUpdated, commentsUpdated);
    }
}
