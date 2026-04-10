package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.JournalEntry;
import com.echo.dto.response.DetailedStatsResponse;
import com.echo.dto.response.OnThisDayResponse;
import com.echo.dto.response.ProfileSummaryResponse;
import com.echo.domain.user.UserProfileSummary;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserProfileSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final AnalysisResultRepository analysisResultRepo;
    private final JournalEntryRepository journalEntryRepo;
    private final UserProfileSummaryRepository profileSummaryRepo;

    @Transactional(readOnly = true)
    public ProfileSummaryResponse getProfileSummary(UUID userId) {
        return profileSummaryRepo.findByUserId(userId)
                .map(ProfileSummaryResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public DetailedStatsResponse getDetailedStats(UUID userId) {
        LocalDate now = LocalDate.now();

        // Single query for all entry dates in last 12 weeks (replaces 4+1 separate queries)
        LocalDate twelveWeeksAgo = now.minusWeeks(12);
        List<LocalDate> allDatesLast12Weeks = analysisResultRepo
                .findExistingDatesByUserAndRange(userId, twelveWeeksAgo, now);

        // Weekly consistency: group dates by week
        List<Integer> weeklyConsistency = new ArrayList<>();
        for (int w = 3; w >= 0; w--) {
            LocalDate weekStart = now.minusWeeks(w).with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            int count = (int) allDatesLast12Weeks.stream()
                    .filter(d -> !d.isBefore(weekStart) && !d.isAfter(weekEnd))
                    .count();
            weeklyConsistency.add(count);
        }

        long weeksActive = Math.max(1, ChronoUnit.WEEKS.between(twelveWeeksAgo, now));
        double avgPerWeek = Math.round((double) allDatesLast12Weeks.size() / weeksActive * 10.0) / 10.0;

        // Single query for last 3 months of analysis results (reused for day, topics, emotions, mood)
        LocalDate threeMonthsAgo = now.minusMonths(3);
        List<AnalysisResult> recentResults = analysisResultRepo
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, threeMonthsAgo, now);

        String mostActiveDay = computeMostActiveDay(recentResults);

        // Most active hour: use lightweight projection instead of full entity load
        int mostActiveHour = computeMostActiveHour(userId, threeMonthsAgo, now);

        // Filter this month's results from already-loaded 3-month data (no extra query)
        LocalDate monthStart = now.withDayOfMonth(1);
        List<AnalysisResult> monthResults = recentResults.stream()
                .filter(r -> !r.getEntryDate().isBefore(monthStart))
                .toList();

        List<String> topTopics = topN(monthResults.stream()
                .filter(r -> r.getTopics() != null)
                .flatMap(r -> r.getTopics().stream())
                .collect(Collectors.toList()), 5);

        List<String> topEmotions = topN(monthResults.stream()
                .filter(r -> r.getKeyEmotions() != null)
                .flatMap(r -> r.getKeyEmotions().stream())
                .collect(Collectors.toList()), 5);

        // Mood trend: compute from already-loaded data (no extra queries)
        BigDecimal moodTrend = computeMoodTrendFromResults(recentResults, now);

        // Word count: DB-side aggregation instead of loading all transcripts into memory
        long totalWords = journalEntryRepo.countTotalWordsForStats(userId);
        long entryCount = journalEntryRepo.countEntriesWithTranscript(userId);
        int avgLength = entryCount == 0 ? 0 : (int) (totalWords / entryCount);

        return new DetailedStatsResponse(
                weeklyConsistency,
                avgPerWeek,
                mostActiveDay,
                mostActiveHour,
                topTopics,
                topEmotions,
                moodTrend,
                totalWords,
                avgLength
        );
    }

    @Transactional(readOnly = true)
    public OnThisDayResponse getOnThisDay(UUID userId) {
        LocalDate today = LocalDate.now();
        List<OnThisDayResponse.OnThisDayEntry> entries = new ArrayList<>();

        // Check 1, 2, and 3 years ago
        for (int yearsAgo = 1; yearsAgo <= 3; yearsAgo++) {
            LocalDate pastDate = today.minusYears(yearsAgo);
            analysisResultRepo.findByUserIdAndEntryDate(userId, pastDate)
                    .ifPresent(ar -> entries.add(new OnThisDayResponse.OnThisDayEntry(
                            ar.getJournalEntry().getId().toString(),
                            ar.getEntryDate(),
                            ar.getSummary(),
                            ar.getMoodLabel(),
                            ar.getMoodScore() != null ? ar.getMoodScore().doubleValue() : 0.0,
                            (int) ChronoUnit.YEARS.between(ar.getEntryDate(), today)
                    )));
        }

        return new OnThisDayResponse(entries);
    }

    private String computeMostActiveDay(List<AnalysisResult> results) {
        if (results.isEmpty()) return null;

        Map<DayOfWeek, Long> countByDay = results.stream()
                .collect(Collectors.groupingBy(r -> r.getEntryDate().getDayOfWeek(), Collectors.counting()));

        return countByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse(null);
    }

    private int computeMostActiveHour(UUID userId, LocalDate from, LocalDate to) {
        List<java.sql.Timestamp> timestamps = journalEntryRepo.findRecordedAtByUserAndDateRange(userId, from, to);
        if (timestamps.isEmpty()) return 20; // default evening

        Map<Integer, Long> countByHour = timestamps.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ts -> ts.toLocalDateTime().getHour(),
                        Collectors.counting()
                ));

        return countByHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(20);
    }

    private BigDecimal computeMoodTrendFromResults(List<AnalysisResult> recentResults, LocalDate now) {
        LocalDate thisMonthStart = now.withDayOfMonth(1);
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);

        List<AnalysisResult> thisMonth = recentResults.stream()
                .filter(r -> !r.getEntryDate().isBefore(thisMonthStart))
                .toList();
        List<AnalysisResult> lastMonth = recentResults.stream()
                .filter(r -> !r.getEntryDate().isBefore(lastMonthStart) && r.getEntryDate().isBefore(thisMonthStart))
                .toList();

        if (thisMonth.isEmpty() || lastMonth.isEmpty()) return BigDecimal.ZERO;

        BigDecimal thisAvg = thisMonth.stream()
                .map(AnalysisResult::getMoodScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(thisMonth.size()), 3, RoundingMode.HALF_UP);

        BigDecimal lastAvg = lastMonth.stream()
                .map(AnalysisResult::getMoodScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(lastMonth.size()), 3, RoundingMode.HALF_UP);

        return thisAvg.subtract(lastAvg).setScale(3, RoundingMode.HALF_UP);
    }

    private List<String> topN(List<String> items, int n) {
        return items.stream()
                .collect(Collectors.groupingBy(s -> s.toLowerCase(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
