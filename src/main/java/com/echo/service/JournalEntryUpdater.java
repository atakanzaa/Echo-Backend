package com.echo.service;

import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import com.echo.event.JournalAnalysisCompletedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Extracted from JournalService to fix @Transactional self-invocation.
// Spring AOP proxies do not intercept calls within the same bean,
// so @Transactional on private/protected methods called from @Async was dead code.
@Component
@RequiredArgsConstructor
public class JournalEntryUpdater {

    private final JournalEntryRepository journalEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;
    private final AIProviderRouter router;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void updateStatus(UUID entryId, EntryStatus status) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(status);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    public void setTranscript(UUID entryId, String transcript) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setTranscript(transcript);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    public void markFailed(UUID entryId, String errorMessage) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(EntryStatus.FAILED);
            e.setErrorMessage(errorMessage);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    public void saveAnalysisResult(UUID entryId, UUID userId, AIAnalysisResponse analysis) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        double clampedScore = Math.max(0.0, Math.min(1.0, analysis.moodScore()));
        AnalysisResult result = AnalysisResult.builder()
                .journalEntry(entry)
                .user(user)
                .entryDate(entry.getEntryDate())
                .summary(analysis.summary())
                .moodScore(BigDecimal.valueOf(clampedScore))
                .moodLabel(analysis.moodLabel())
                .topics(analysis.topics())
                .reflectiveQuestion(analysis.reflectiveQuestion())
                .keyEmotions(analysis.keyEmotions())
                .energyLevel(analysis.energyLevel())
                .rawAiResponse(analysis.rawJson())
                .aiProvider(router.activeProvider())
                .build();

        analysisResultRepository.save(result);

        eventPublisher.publishEvent(new JournalAnalysisCompletedEvent(userId, entryId, analysis));

        updateUserMoodAverage(userId);
    }

    @Transactional
    public void updateUserMoodAverage(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            var results = analysisResultRepository
                    .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                            userId, LocalDate.now().minusDays(30), LocalDate.now());
            if (!results.isEmpty()) {
                double avg = results.stream()
                        .mapToDouble(r -> r.getMoodScore().doubleValue())
                        .average().orElse(0.0);
                user.setMoodScoreAvg(BigDecimal.valueOf(avg));
                userRepository.save(user);
            }
        });
    }
}
