package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.MoodCategory;
import com.echo.domain.journal.SummaryPeriod;
import com.echo.domain.subscription.FeatureKey;
import com.echo.dto.response.SummaryResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final AnalysisResultRepository analysisResultRepository;
    private final AISynthesisService       synthesisService;
    private final UserRepository           userRepository;
    private final EntitlementService       entitlementService;

    @Transactional(readOnly = true)
    public SummaryResponse getSummary(UUID userId, SummaryPeriod period, LocalDate endDate) {
        int maxPeriod = entitlementService.getLimit(userId, FeatureKey.SUMMARY_MAX_PERIOD);
        if (maxPeriod != -1 && period.getDays() > maxPeriod) {
            throw new QuotaExceededException(
                    "SUMMARY_PERIOD_LOCKED",
                    "Requested summary period is locked for your tier. Upgrade to Premium for longer periods."
            );
        }

        LocalDate startDate = endDate.minusDays(period.getDays() - 1);
        List<AnalysisResult> results = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, startDate, endDate);

        if (results.isEmpty()) {
            return SummaryResponse.empty(startDate, endDate, period.getDays());
        }

        double avgMood  = results.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).average().orElse(0);
        double highMood = results.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).max().orElse(0);
        double lowMood  = results.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).min().orElse(0);

        String moodTrend = MoodStatisticsCalculator.computeMoodTrend(results);

        // Top topics
        List<String> dominantTopics = results.stream()
                .flatMap(r -> r.getTopics() != null ? r.getTopics().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Top emotions
        List<String> topEmotions = results.stream()
                .flatMap(r -> r.getKeyEmotions() != null ? r.getKeyEmotions().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        // Energy distribution
        Map<String, Integer> energyDist = new HashMap<>();
        energyDist.put("high",   (int) results.stream().filter(r -> "high".equals(r.getEnergyLevel())).count());
        energyDist.put("medium", (int) results.stream().filter(r -> "medium".equals(r.getEnergyLevel())).count());
        energyDist.put("low",    (int) results.stream().filter(r -> "low".equals(r.getEnergyLevel())).count());

        // Best/worst day
        String bestDay  = results.stream().max(Comparator.comparing(r -> r.getMoodScore().doubleValue()))
                .map(r -> r.getEntryDate().toString()).orElse(null);
        String worstDay = results.stream().min(Comparator.comparing(r -> r.getMoodScore().doubleValue()))
                .map(r -> r.getEntryDate().toString()).orElse(null);

        double coverage = (results.size() * 100.0) / period.getDays();

        // daily mood data — for Insights weekly chart and emotion distribution
        List<SummaryResponse.DailyMoodScore> dailyMoodScores = results.stream()
                .map(r -> new SummaryResponse.DailyMoodScore(
                        r.getEntryDate().toString(),
                        r.getMoodScore().doubleValue(),
                        r.getMoodLabel(),
                        MoodCategory.fromScore(r.getMoodScore().doubleValue()).name()))
                .sorted(Comparator.comparing(SummaryResponse.DailyMoodScore::date))
                .toList();
        Map<String, Double> moodCategoryDistribution =
                MoodStatisticsCalculator.computeMoodCategoryDistribution(results);

        return new SummaryResponse(
                startDate.toString(),
                endDate.toString(),
                period.getDays(),
                results.size(),
                BigDecimal.valueOf(coverage).setScale(1, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal.valueOf(avgMood).setScale(3, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal.valueOf(highMood).setScale(3, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal.valueOf(lowMood).setScale(3, RoundingMode.HALF_UP).doubleValue(),
                moodTrend,
                dominantTopics,
                topEmotions,
                energyDist,
                moodCategoryDistribution,
                bestDay,
                worstDay,
                buildNarrativeSummary(userId, results, period, avgMood,
                        userRepository.findById(userId).map(u -> u.getPreferredLanguage()).orElse("tr")),
                dailyMoodScores
        );
    }

    private String buildNarrativeSummary(UUID userId, List<AnalysisResult> results,
                                          SummaryPeriod period, double avgMood, String language) {
        try {
            var synthesis = synthesisService.getLatestCachedSynthesis(userId);
            if (synthesis != null && synthesis.narrativeSummary() != null) return synthesis.narrativeSummary();
        } catch (Exception e) {
            log.warn("AI narrative failed, using fallback: {}", e.getMessage());
        }
        // Fallback: template
        if ("en".equals(language)) {
            String sentiment = avgMood > 0.7 ? "positive" : avgMood > 0.4 ? "balanced" : "challenging";
            return String.format("%d entries recorded over this %d-day period. Overall emotional tone appears %s.",
                    results.size(), period.getDays(), sentiment);
        }
        String sentiment = avgMood > 0.7 ? "olumlu" : avgMood > 0.4 ? "dengeli" : "zorlu";
        return String.format("Bu %d günlük dönemde %d giriş yapıldı. Genel duygusal ton %s görünüyor.",
                period.getDays(), results.size(), sentiment);
    }
}
