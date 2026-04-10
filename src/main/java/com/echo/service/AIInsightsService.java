package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.subscription.FeatureKey;
import com.echo.dto.response.AIInsightsResponse;
import com.echo.dto.response.InsightsPeriodEligibilityResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIInsightsService {

    private static final List<PeriodUnlockRule> PERIOD_RULES = List.of(
            new PeriodUnlockRule(7, 1, 1),
            new PeriodUnlockRule(30, 5, 5),
            new PeriodUnlockRule(90, 15, 10),
            new PeriodUnlockRule(180, 30, 20),
            new PeriodUnlockRule(365, 60, 40)
    );

    private final AnalysisResultRepository analysisResultRepository;
    private final AISynthesisService       synthesisService;
    private final UserRepository           userRepository;
    private final EntitlementService       entitlementService;

    @Transactional(readOnly = true)
    public InsightsPeriodEligibilityResponse getEligibility(UUID userId) {
        int totalEntries = toInt(analysisResultRepository.countByUserId(userId));
        int totalDistinctDays = toInt(analysisResultRepository.countDistinctEntryDatesByUserId(userId));

        List<InsightsPeriodEligibilityResponse.PeriodOption> periods = PERIOD_RULES.stream()
                .map(rule -> toPeriodOption(rule, totalEntries, totalDistinctDays))
                .toList();

        return new InsightsPeriodEligibilityResponse(periods, totalEntries, totalDistinctDays);
    }

    @Transactional(readOnly = true)
    public AIInsightsResponse getInsights(UUID userId, int periodDays) {
        int maxPeriod = entitlementService.getLimit(userId, FeatureKey.INSIGHTS_MAX_PERIOD);
        if (maxPeriod != -1 && periodDays > maxPeriod) {
            throw new QuotaExceededException(
                    "INSIGHTS_PERIOD_LOCKED",
                    "Requested insights period is locked for your tier. Upgrade to Premium for longer periods."
            );
        }

        PeriodUnlockRule rule = getRule(periodDays);
        int totalEntries = toInt(analysisResultRepository.countByUserId(userId));
        int totalDistinctDays = toInt(analysisResultRepository.countDistinctEntryDatesByUserId(userId));
        InsightsPeriodEligibilityResponse.PeriodOption eligibility =
                toPeriodOption(rule, totalEntries, totalDistinctDays);

        if (!eligibility.unlocked()) {
            throw new IllegalArgumentException(String.format(
                    "Period %d is locked. Need at least %d entries across %d distinct days (current: %d entries, %d distinct days).",
                    periodDays,
                    eligibility.requiredEntries(),
                    eligibility.requiredDistinctDays(),
                    eligibility.currentEntries(),
                    eligibility.currentDistinctDays()
            ));
        }

        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays - 1);

        List<AnalysisResult> current = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, startDate, endDate);
        List<AnalysisResult> previous = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                        userId,
                        startDate.minusDays(periodDays),
                        startDate.minusDays(1)
                );

        double currentAvg = MoodStatisticsCalculator.computeAverageMood(current);
        OptionalDouble previousAvg = previous.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).average();
        boolean hasPreviousPeriodData = previousAvg.isPresent();
        double emotionalTrend = hasPreviousPeriodData
                ? (currentAvg - previousAvg.getAsDouble()) * 10.0
                : 0.0;

        // Top themes
        Map<String, Long> topicCounts = current.stream()
                .flatMap(r -> r.getTopics() != null ? r.getTopics().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        Map<String, Long> prevTopicCounts = previous.stream()
                .flatMap(r -> r.getTopics() != null ? r.getTopics().stream() : java.util.stream.Stream.empty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        List<AIInsightsResponse.Theme> themes = topicCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(7)
                .map(e -> new AIInsightsResponse.Theme(
                        e.getKey(),
                        e.getValue().intValue(),
                        e.getValue() > prevTopicCounts.getOrDefault(e.getKey(), 0L)
                ))
                .toList();

        // AI Suggestions based on patterns
        String language = userRepository.findById(userId).map(u -> u.getPreferredLanguage()).orElse("tr");
        List<AIInsightsResponse.Suggestion> suggestions = buildSuggestions(userId, periodDays, current, language);

        String moodTrend = MoodStatisticsCalculator.computeMoodTrend(current);
        boolean hasSufficientData = !current.isEmpty();

        return new AIInsightsResponse(
                periodDays,
                currentAvg,
                moodTrend,
                themes,
                suggestions,
                emotionalTrend,
                hasSufficientData,
                hasPreviousPeriodData,
                current.size()
        );
    }

    private List<AIInsightsResponse.Suggestion> buildSuggestions(UUID userId, int periodDays,
                                                                    List<AnalysisResult> results,
                                                                    String language) {
        try {
            var synthesis = synthesisService.synthesize(userId, periodDays);
            if (synthesis.suggestions() != null && !synthesis.suggestions().isEmpty()) {
                return synthesis.suggestions().stream()
                        .map(s -> new AIInsightsResponse.Suggestion(s.title(), s.body(), s.icon()))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("AI suggestions failed, using fallback: {}", e.getMessage());
        }
        // fallback: rule-based
        return buildFallbackSuggestions(results, language);
    }

    private List<AIInsightsResponse.Suggestion> buildFallbackSuggestions(List<AnalysisResult> results,
                                                                           String language) {
        boolean en = "en".equals(language);
        java.util.List<AIInsightsResponse.Suggestion> suggestions = new java.util.ArrayList<>();
        long lowEnergyCount = results.stream().filter(r -> "low".equals(r.getEnergyLevel())).count();
        if (lowEnergyCount > results.size() / 2) {
            suggestions.add(new AIInsightsResponse.Suggestion(
                    en ? "Energy-boosting habits"       : "Enerji artırıcı alışkanlıklar",
                    en ? "Your energy has been low lately. Try reviewing your morning routine."
                       : "Son dönemde enerji seviyeniz düşük görünüyor. Sabah rutinlerinizi gözden geçirmeyi deneyin.",
                    "battery.25"
            ));
        }
        long negativeCount = results.stream()
                .filter(r -> r.getMoodScore().doubleValue() < 0.4).count();
        if (negativeCount > 2) {
            suggestions.add(new AIInsightsResponse.Suggestion(
                    en ? "Breathing exercises"              : "Nefes egzersizleri",
                    en ? "On difficult days, short meditation sessions can help."
                       : "Zor günler yaşadığınızda kısa meditasyon seansları yardımcı olabilir.",
                    "wind"
            ));
        }
        return suggestions;
    }

    private PeriodUnlockRule getRule(int periodDays) {
        return PERIOD_RULES.stream()
                .filter(rule -> rule.days() == periodDays)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported period. Allowed values: 7, 30, 90, 180, 365"));
    }

    private InsightsPeriodEligibilityResponse.PeriodOption toPeriodOption(PeriodUnlockRule rule,
                                                                           int totalEntries,
                                                                           int totalDistinctDays) {
        boolean unlocked = totalEntries >= rule.requiredEntries()
                && totalDistinctDays >= rule.requiredDistinctDays();
        return new InsightsPeriodEligibilityResponse.PeriodOption(
                rule.days(),
                unlocked,
                rule.requiredEntries(),
                totalEntries,
                rule.requiredDistinctDays(),
                totalDistinctDays
        );
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record PeriodUnlockRule(int days, int requiredEntries, int requiredDistinctDays) {}
}
