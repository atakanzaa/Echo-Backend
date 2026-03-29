package com.echo.service;

import com.echo.domain.journal.MoodCategory;
import com.echo.dto.response.CalendarMonthResponse;
import com.echo.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final AnalysisResultRepository analysisResultRepository;

    @Transactional(readOnly = true)
    public CalendarMonthResponse getMonth(UUID userId, int year, int month) {
        YearMonth  yearMonth = YearMonth.of(year, month);
        LocalDate  startDate = yearMonth.atDay(1);
        LocalDate  endDate   = yearMonth.atEndOfMonth();

        List<com.echo.domain.journal.AnalysisResult> results = analysisResultRepository
                .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, startDate, endDate);

        Map<LocalDate, com.echo.domain.journal.AnalysisResult> byDate = results.stream()
                .collect(Collectors.toMap(
                        com.echo.domain.journal.AnalysisResult::getEntryDate,
                        r -> r,
                        (a, b) -> a  // keep first entry if multiple per day
                ));

        List<CalendarMonthResponse.CalendarDay> days = new ArrayList<>();
        for (int d = 1; d <= yearMonth.lengthOfMonth(); d++) {
            LocalDate date    = yearMonth.atDay(d);
            var       result  = byDate.get(date);
            double score    = result != null && result.getMoodScore() != null ? result.getMoodScore().doubleValue() : 0.0;
            String category = result != null ? MoodCategory.fromScore(score).name() : null;
            days.add(new CalendarMonthResponse.CalendarDay(
                    d,
                    result != null ? result.getMoodLabel() : null,
                    result != null ? result.getJournalEntry().getId().toString() : null,
                    score,
                    category
            ));
        }

        return new CalendarMonthResponse(year, month, days);
    }
}
