package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.MoodCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MoodStatisticsCalculator {

    private static final List<MoodCategory> ORDER = List.of(
            MoodCategory.STRESS,
            MoodCategory.NEUTRAL,
            MoodCategory.CALM,
            MoodCategory.MOTIVATED
    );

    private MoodStatisticsCalculator() {}

    public static Map<String, Double> computeMoodCategoryDistribution(List<AnalysisResult> results) {
        Map<MoodCategory, Long> counts = new EnumMap<>(MoodCategory.class);
        ORDER.forEach(category -> counts.put(category, 0L));

        for (AnalysisResult result : results) {
            MoodCategory category = MoodCategory.fromScore(result.getMoodScore().doubleValue());
            counts.put(category, counts.get(category) + 1);
        }

        Map<String, Double> distribution = new LinkedHashMap<>();
        if (results.isEmpty()) {
            ORDER.forEach(category -> distribution.put(category.name(), 0.0));
            return distribution;
        }

        Map<MoodCategory, Double> rounded = new EnumMap<>(MoodCategory.class);
        int total = results.size();
        double sum = 0.0;
        for (MoodCategory category : ORDER) {
            double percentage = (counts.get(category) * 100.0) / total;
            double roundedPercentage = roundOneDecimal(percentage);
            rounded.put(category, roundedPercentage);
            sum += roundedPercentage;
        }

        double diff = roundOneDecimal(100.0 - sum);
        if (Math.abs(diff) > 0.0) {
            MoodCategory largest = ORDER.stream()
                    .max(Comparator.comparingLong(counts::get))
                    .orElse(MoodCategory.NEUTRAL);
            rounded.put(largest, roundOneDecimal(rounded.get(largest) + diff));
        }

        for (MoodCategory category : ORDER) {
            double value = rounded.getOrDefault(category, 0.0);
            distribution.put(category.name(), Math.abs(value) < 1e-9 ? 0.0 : value);
        }

        return distribution;
    }

    public static String computeMoodTrend(List<AnalysisResult> results) {
        if (results.size() < 2) {
            return "stable";
        }

        int half = results.size() / 2;
        double recentAvg = computeAverageMood(results.subList(0, half));
        double olderAvg = computeAverageMood(results.subList(half, results.size()));

        if (recentAvg > olderAvg + 0.05) {
            return "improving";
        }
        if (recentAvg < olderAvg - 0.05) {
            return "declining";
        }
        return "stable";
    }

    public static double computeAverageMood(List<AnalysisResult> results) {
        return results.stream()
                .mapToDouble(result -> result.getMoodScore().doubleValue())
                .average()
                .orElse(0.0);
    }

    private static double roundOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
