package com.echo.service;

import com.echo.ai.AIAnalysisRequest;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.journal.AiJobDlq;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import com.echo.event.JournalAnalysisCompletedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AiJobDlqRepository;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobDlqService {

    private final AiJobDlqRepository aiJobDlqRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;
    private final AIProviderRouter router;
    private final JournalEntryUpdater entryUpdater;
    private final AchievementService achievementService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_RETRY_BATCH_SIZE = 25;
    private static final int CLAIM_TTL_MINUTES = 10;

    @Transactional
    public void enqueue(UUID entryId, String jobType, String errorCode, String payload) {
        OffsetDateTime now = OffsetDateTime.now();
        AiJobDlq dlq = AiJobDlq.builder()
                .journalEntryId(entryId)
                .jobType(jobType)
                .errorCode(errorCode)
                .errorMessage(errorCode)
                .payload(payload)
                .attemptCount(1)
                .firstFailedAt(now)
                .lastFailedAt(now)
                .nextRetryAt(now.plusMinutes(5))
                .build();
        aiJobDlqRepository.save(dlq);
    }

    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void retryPendingJobs() {
        List<AiJobDlq> jobs = claimRetryableJobs();
        if (jobs.isEmpty()) {
            return;
        }

        log.info("Retrying {} DLQ jobs", jobs.size());
        for (AiJobDlq job : jobs) {
            try {
                replay(job);
                markResolved(job, "SUCCESS");
            } catch (Exception e) {
                handleRetryFailure(job, e);
            }
        }
    }

    private List<AiJobDlq> claimRetryableJobs() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            List<AiJobDlq> jobs = aiJobDlqRepository.findRetryableJobsForUpdate(now, MAX_RETRY_BATCH_SIZE);
            OffsetDateTime claimUntil = now.plusMinutes(CLAIM_TTL_MINUTES);
            jobs.forEach(job -> job.setNextRetryAt(claimUntil));
            return aiJobDlqRepository.saveAll(jobs);
        });
    }

    @Transactional
    protected void replay(AiJobDlq job) {
        if (!"ANALYSIS".equalsIgnoreCase(job.getJobType())) {
            throw new IllegalArgumentException("Unsupported DLQ job type: " + job.getJobType());
        }
        replayAnalysis(job);
    }

    private void replayAnalysis(AiJobDlq job) {
        JournalEntry entry = journalEntryRepository.findById(job.getJournalEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found for DLQ replay"));

        if (analysisResultRepository.findByJournalEntryId(entry.getId()).isPresent()) {
            entryUpdater.updateStatus(entry.getId(), EntryStatus.COMPLETE);
            return;
        }

        String transcript = entry.getTranscript();
        if (transcript == null || transcript.isBlank()) {
            throw new IllegalStateException("Transcript missing for analysis replay");
        }

        User user = userRepository.findById(entry.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for DLQ replay"));

        AIAnalysisResponse analysis = router.analysis()
                .analyze(new AIAnalysisRequest(transcript, user.getTimezone(), user.getPreferredLanguage()));

        saveAnalysisResult(entry, user, analysis);
        entryUpdater.updateStatus(entry.getId(), EntryStatus.COMPLETE);
        achievementService.checkAndAward(user.getId());
    }

    @Transactional
    protected void saveAnalysisResult(JournalEntry entry, User user, AIAnalysisResponse analysis) {
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

        eventPublisher.publishEvent(new JournalAnalysisCompletedEvent(user.getId(), entry.getId(), analysis));
        updateUserMoodAverage(user.getId());
    }

    @Transactional
    protected void updateUserMoodAverage(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            List<AnalysisResult> results = analysisResultRepository
                    .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                            userId, LocalDate.now().minusDays(30), LocalDate.now());
            if (!results.isEmpty()) {
                double avg = results.stream()
                        .mapToDouble(r -> r.getMoodScore().doubleValue())
                        .average()
                        .orElse(0.0);
                user.setMoodScoreAvg(BigDecimal.valueOf(avg));
                userRepository.save(user);
            }
        });
    }

    @Transactional
    protected void markResolved(AiJobDlq job, String resolution) {
        job.setResolvedAt(OffsetDateTime.now());
        job.setResolution(resolution);
        aiJobDlqRepository.save(job);
    }

    @Transactional
    protected void handleRetryFailure(AiJobDlq job, Exception e) {
        int nextAttempt = job.getAttemptCount() + 1;
        job.setAttemptCount(nextAttempt);
        job.setLastFailedAt(OffsetDateTime.now());
        job.setErrorCode(classifyError(e));
        job.setErrorMessage(e.getMessage());

        if (nextAttempt >= 5) {
            job.setResolvedAt(OffsetDateTime.now());
            job.setResolution("ABANDONED");
            log.error("DLQ job abandoned after max retries: jobId={} entryId={}",
                    job.getId(), job.getJournalEntryId(), e);
        } else {
            long minutes = 5L * (1L << nextAttempt);
            job.setNextRetryAt(OffsetDateTime.now().plusMinutes(minutes));
            log.warn("DLQ retry failed: jobId={} attempt={} nextRetryAt={}",
                    job.getId(), nextAttempt, job.getNextRetryAt(), e);
        }
        aiJobDlqRepository.save(job);
    }

    private String classifyError(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "TIMEOUT";
        }
        if (msg.contains("rate") || msg.contains("quota")) {
            return "RATE_LIMITED";
        }
        if (e instanceof IllegalArgumentException) {
            return "PARSE_ERROR";
        }
        return "SERVER_ERROR";
    }
}
