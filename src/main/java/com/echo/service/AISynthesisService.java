package com.echo.service;

import com.echo.ai.AIProviderRouter;
import com.echo.ai.AISynthesisRequest;
import com.echo.ai.AISynthesisResponse;
import com.echo.domain.coach.CoachMessage;
import com.echo.domain.coach.MessageRole;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.user.User;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachMessageRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.echo.exception.ServiceUnavailableException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI Synthesis ara katmanı.
 * Summary, Insights ve Achievements servisleri bu servisi kullanır — doğrudan provider çağırmaz.
 * Caffeine cache ile aynı veri için tekrarlanan AI çağrılarını önler.
 * Her synthesis sonrası UserMemoryService üzerinden kullanıcı profili güncellenir (adaptive learning).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AISynthesisService {

    private final AIProviderRouter         router;
    private final UserMemoryService        memoryService;
    private final AnalysisResultRepository analysisRepo;
    private final CoachMessageRepository   coachRepo;
    private final GoalRepository           goalRepo;
    private final UserRepository           userRepo;
    private final Cache<String, AISynthesisResponse> synthesisCache;

    private static final int MAX_ENTRIES         = 30;
    private static final int MAX_COACH_EXCHANGES = 20;
    private static final int MAX_TOPICS          = 5;
    private static final int MAX_EMOTIONS        = 5;
    private static final int MAX_GOALS           = 5;
    private static final int SUMMARY_MAX_CHARS   = 200;

    @Transactional(readOnly = true, noRollbackFor = ServiceUnavailableException.class)
    public AISynthesisResponse synthesize(UUID userId, int periodDays) {
        String cacheKey = buildCacheKey(userId, periodDays);
        AISynthesisResponse cached = synthesisCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Synthesis cache hit: userId={}, period={}", userId, periodDays);
            return cached;
        }

        AISynthesisRequest request = buildRequest(userId, periodDays);
        AISynthesisResponse response = router.synthesis().synthesize(request);

        // adaptive learning: update memory after each synthesis
        memoryService.updateFromSynthesis(userId, response);

        synthesisCache.put(cacheKey, response);
        log.info("Synthesis tamamlandı: userId={}, period={}, growthScore={}",
                userId, periodDays, response.growthScore());
        return response;
    }

    /**
     * Konuşma bazlı async synthesis tetikleyici.
     * CoachService tarafından her 5. mesaj alışverişi veya oturum bitiminde çağrılır.
     * Virtual thread üzerinde çalışır — coach yanıtını asla engellemez.
     */
    /**
     * Session-end trigger: synthesizes only today's data (period=1).
     * Fast and focused — captures what happened today without re-processing old history.
     * The 7/30/90-day deep synthesis runs on-demand from the Insights page.
     */
    @Async
    public void synthesizeAsync(UUID userId) {
        try {
            synthesize(userId, 1);
            log.debug("Async synthesis tamamlandı: userId={}", userId);
        } catch (Exception e) {
            log.warn("Async synthesis başarısız: userId={}, hata={}", userId, e.getMessage());
        }
    }

    // ── Cache Key ─────────────────────────────────────────────────────────────

    private String buildCacheKey(UUID userId, int periodDays) {
        // latest journal entry OR coach message time → new conversation = cache miss
        OffsetDateTime lastEntry = analysisRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(AnalysisResult::getCreatedAt)
                .orElse(OffsetDateTime.MIN);
        OffsetDateTime lastCoach = coachRepo.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(CoachMessage::getCreatedAt)
                .orElse(OffsetDateTime.MIN);
        OffsetDateTime lastUpdate = lastEntry.isAfter(lastCoach) ? lastEntry : lastCoach;
        return userId + ":" + periodDays + ":" + lastUpdate.toEpochSecond();
    }

    // ── Request Builder ────────────────────────────────────────────────────────

    private AISynthesisRequest buildRequest(UUID userId, int periodDays) {
        LocalDate end   = LocalDate.now();
        LocalDate start = end.minusDays(periodDays - 1);

        // Journal entries: max 30, summary max 200 chars
        List<AnalysisResult> analyses = analysisRepo
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, start, end);
        List<AISynthesisRequest.EntrySummary> entries = analyses.stream()
                .limit(MAX_ENTRIES)
                .map(a -> new AISynthesisRequest.EntrySummary(
                        a.getEntryDate().toString(),
                        a.getMoodScore().doubleValue(),
                        a.getMoodLabel(),
                        a.getTopics() != null
                                ? a.getTopics().stream().limit(MAX_TOPICS).toList()
                                : List.of(),
                        a.getKeyEmotions() != null
                                ? a.getKeyEmotions().stream().limit(MAX_EMOTIONS).toList()
                                : List.of(),
                        a.getEnergyLevel(),
                        truncate(a.getSummary(), SUMMARY_MAX_CHARS)
                ))
                .toList();

        // coach exchanges: USER-ASSISTANT pairs from last N days, max 20
        OffsetDateTime since = OffsetDateTime.now().minusDays(periodDays);
        List<CoachMessage> messages = coachRepo
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(userId, since);
        List<AISynthesisRequest.CoachExchange> exchanges = buildCoachExchanges(messages);

        // Active goals: max 5
        List<String> activeGoals = goalRepo
                .findByUserIdAndStatusOrderByDetectedAtDesc(userId, "PENDING")
                .stream()
                .limit(MAX_GOALS)
                .map(g -> g.getTimeframe() != null && !g.getTimeframe().isBlank()
                        ? g.getTitle() + " (" + g.getTimeframe() + ")"
                        : g.getTitle())
                .toList();

        int completedCount = goalRepo.countByUserIdAndStatus(userId, "COMPLETED");

        User user = userRepo.findById(userId).orElseThrow();
        String userProfile = memoryService.getUserProfile(userId);

        return new AISynthesisRequest(
                periodDays,
                entries,
                exchanges,
                activeGoals,
                completedCount,
                user.getCurrentStreak(),
                user.getTotalEntries(),
                userProfile,
                null,  // previousPeriodTrend — future iteration
                user.getPreferredLanguage()
        );
    }

    // ── Coach Exchange Builder ─────────────────────────────────────────────────

    private List<AISynthesisRequest.CoachExchange> buildCoachExchanges(List<CoachMessage> messages) {
        List<AISynthesisRequest.CoachExchange> pairs = new ArrayList<>();
        for (int i = 0; i < messages.size() - 1 && pairs.size() < MAX_COACH_EXCHANGES; i++) {
            CoachMessage m    = messages.get(i);
            CoachMessage next = messages.get(i + 1);
            if (m.getRole() == MessageRole.USER && next.getRole() == MessageRole.ASSISTANT) {
                pairs.add(new AISynthesisRequest.CoachExchange(
                        m.getCreatedAt().toLocalDate().toString(),
                        truncate(m.getContent(), 150),
                        truncate(next.getContent(), 150)
                ));
                i++;  // skip pair, consumed both
            }
        }
        return pairs;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
