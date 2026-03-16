package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.dto.response.AIInsightsResponse;
import com.echo.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIInsightsService {

    private final AnalysisResultRepository analysisResultRepository;
    private final AISynthesisService       synthesisService;

    @Transactional(readOnly = true)
    public AIInsightsResponse getInsights(UUID userId, int periodDays) {
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

        double currentAvg  = current.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).average().orElse(0.5);
        double previousAvg = previous.stream().mapToDouble(r -> r.getMoodScore().doubleValue()).average().orElse(0.5);
        double emotionalTrend = (currentAvg - previousAvg) * 100;

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
        List<AIInsightsResponse.Suggestion> suggestions = buildSuggestions(userId, periodDays, current);

        String moodTrend = emotionalTrend > 5 ? "improving" : emotionalTrend < -5 ? "declining" : "stable";

        return new AIInsightsResponse(
                periodDays,
                currentAvg,
                moodTrend,
                themes,
                suggestions,
                emotionalTrend
        );
    }

    private List<AIInsightsResponse.Suggestion> buildSuggestions(UUID userId, int periodDays,
                                                                    List<AnalysisResult> results) {
        try {
            var synthesis = synthesisService.synthesize(userId, periodDays);
            if (synthesis.suggestions() != null && !synthesis.suggestions().isEmpty()) {
                return synthesis.suggestions().stream()
                        .map(s -> new AIInsightsResponse.Suggestion(s.title(), s.body(), s.icon()))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("AI suggestions oluşturulamadı, fallback kullanılıyor: {}", e.getMessage());
        }
        // Fallback: kural tabanlı
        return buildFallbackSuggestions(results);
    }

    private List<AIInsightsResponse.Suggestion> buildFallbackSuggestions(List<AnalysisResult> results) {
        java.util.List<AIInsightsResponse.Suggestion> suggestions = new java.util.ArrayList<>();
        long lowEnergyCount = results.stream().filter(r -> "low".equals(r.getEnergyLevel())).count();
        if (lowEnergyCount > results.size() / 2) {
            suggestions.add(new AIInsightsResponse.Suggestion(
                    "Enerji artırıcı alışkanlıklar",
                    "Son dönemde enerji seviyeniz düşük görünüyor. Sabah rutinlerinizi gözden geçirmeyi deneyin.",
                    "battery.25"
            ));
        }
        long negativeCount = results.stream()
                .filter(r -> r.getMoodScore().doubleValue() < 0.4).count();
        if (negativeCount > 2) {
            suggestions.add(new AIInsightsResponse.Suggestion(
                    "Nefes egzersizleri",
                    "Zor günler yaşadığınızda kısa meditasyon seansları yardımcı olabilir.",
                    "wind"
            ));
        }
        return suggestions;
    }
}
