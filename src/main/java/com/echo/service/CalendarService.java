package com.echo.service;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.MoodCategory;
import com.echo.dto.response.CalendarMonthResponse;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.CoachSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final AnalysisResultRepository analysisResultRepository;
    private final CoachSessionRepository coachSessionRepository;

    @Transactional(readOnly = true)
    public CalendarMonthResponse getMonth(UUID userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate   = yearMonth.atEndOfMonth();

        List<AnalysisResult> results = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, startDate, endDate);

        // Group all results by date (multiple entries per day possible)
        Map<LocalDate, List<AnalysisResult>> byDate = results.stream()
                .collect(Collectors.groupingBy(AnalysisResult::getEntryDate));

        // Coach session dates in this month
        OffsetDateTime monthStart = startDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime monthEnd = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        Set<LocalDate> coachDates = coachSessionRepository
                .findSessionDatesByUserAndRange(userId, monthStart, monthEnd)
                .stream()
                .map(java.sql.Date::toLocalDate)
                .collect(Collectors.toSet());

        List<CalendarMonthResponse.CalendarDay> days = new ArrayList<>();
        int streakDays = 0;
        int totalEntries = 0;
        BigDecimal moodSum = BigDecimal.ZERO;
        int moodCount = 0;

        for (int d = 1; d <= yearMonth.lengthOfMonth(); d++) {
            LocalDate date = yearMonth.atDay(d);
            List<AnalysisResult> dayResults = byDate.getOrDefault(date, Collections.emptyList());

            // Use the first result for primary mood display
            AnalysisResult primary = dayResults.isEmpty() ? null : dayResults.getFirst();

            double score = primary != null && primary.getMoodScore() != null
                    ? primary.getMoodScore().doubleValue() : 0.0;
            String category = primary != null ? MoodCategory.fromScore(score).name() : null;

            // Dominant emotion across all entries for this day
            String dominantEmotion = dayResults.stream()
                    .filter(r -> r.getKeyEmotions() != null && !r.getKeyEmotions().isEmpty())
                    .flatMap(r -> r.getKeyEmotions().stream())
                    .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            int entryCount = dayResults.size();
            totalEntries += entryCount;

            if (!dayResults.isEmpty()) {
                streakDays++;
                for (AnalysisResult r : dayResults) {
                    if (r.getMoodScore() != null) {
                        moodSum = moodSum.add(r.getMoodScore());
                        moodCount++;
                    }
                }
            }

            days.add(new CalendarMonthResponse.CalendarDay(
                    d,
                    primary != null ? primary.getMoodLabel() : null,
                    primary != null ? primary.getJournalEntry().getId().toString() : null,
                    score,
                    category,
                    entryCount,
                    dominantEmotion,
                    coachDates.contains(date)
            ));
        }

        BigDecimal averageMood = moodCount > 0
                ? moodSum.divide(BigDecimal.valueOf(moodCount), 3, RoundingMode.HALF_UP)
                : null;

        CalendarMonthResponse.MonthSummary summary =
                new CalendarMonthResponse.MonthSummary(averageMood, totalEntries, streakDays);

        return new CalendarMonthResponse(year, month, days, summary);
    }
}
