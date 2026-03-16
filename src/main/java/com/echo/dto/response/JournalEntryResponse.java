package com.echo.dto.response;

import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.JournalEntry;

import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID   id,
        String recordedAt,
        String entryDate,
        String audioUrl,
        Integer audioDurationSeconds,
        String transcript,
        String status,
        String errorMessage,
        AnalysisResultResponse analysis,
        String createdAt
) {
    public static JournalEntryResponse from(JournalEntry entry, AnalysisResult analysis) {
        return new JournalEntryResponse(
                entry.getId(),
                entry.getRecordedAt() != null ? entry.getRecordedAt().toString() : null,
                entry.getEntryDate() != null   ? entry.getEntryDate().toString()   : null,
                null, // audioUrl gizlilik için dönmüyoruz
                entry.getAudioDurationSeconds(),
                entry.getTranscript(),
                entry.getStatus().name().toLowerCase(),
                entry.getErrorMessage(),
                analysis != null ? AnalysisResultResponse.from(analysis) : null,
                entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null
        );
    }

    public record AnalysisResultResponse(
            String       summary,
            double       moodScore,
            String       moodLabel,
            List<String> topics,
            String       reflectiveQuestion,
            List<String> keyEmotions,
            String       energyLevel
    ) {
        public static AnalysisResultResponse from(AnalysisResult result) {
            return new AnalysisResultResponse(
                    result.getSummary(),
                    result.getMoodScore().doubleValue(),
                    result.getMoodLabel(),
                    result.getTopics(),
                    result.getReflectiveQuestion(),
                    result.getKeyEmotions(),
                    result.getEnergyLevel()
            );
        }
    }
}
