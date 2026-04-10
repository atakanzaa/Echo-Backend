package com.echo.service;

import com.echo.ai.AIAnalysisRequest;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.response.JournalEntryResponse;
import com.echo.dto.response.JournalStatusResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntryRepository journalEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;
    private final AIProviderRouter router;
    private final AchievementService achievementService;
    private final AiJobDlqService aiJobDlqService;
    // separate bean fixes @Transactional self-invocation in @Async methods
    private final JournalEntryUpdater entryUpdater;
    private final EntitlementService entitlementService;

    @Transactional
    public JournalEntryResponse createEntry(UUID userId,
                                            byte[] audioBytes,
                                            String filename,
                                            OffsetDateTime recordedAt,
                                            int durationSeconds,
                                            String idempotencyKey) {
        // Deduplicate: return existing entry for repeated uploads with same key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<JournalEntry> existing = journalEntryRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                JournalEntry e = existing.get();
                AnalysisResult analysis = analysisResultRepository.findByJournalEntryId(e.getId()).orElse(null);
                log.info("Idempotent request — returning existing entry: entryId={} key={}", e.getId(), idempotencyKey);
                return JournalEntryResponse.from(e, analysis);
            }
        }

        if (!entitlementService.consumeQuota(userId, FeatureKey.JOURNAL_ENTRIES)) {
            throw new QuotaExceededException(
                    "JOURNAL_LIMIT",
                    "Monthly journal entry limit reached. Upgrade to Premium for unlimited journaling."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        JournalEntry entry = JournalEntry.builder()
                .user(user)
                .recordedAt(recordedAt)
                .entryDate(recordedAt.toLocalDate())
                .audioDurationSeconds(durationSeconds)
                .idempotencyKey(idempotencyKey)
                .status(EntryStatus.UPLOADING)
                .build();

        // saveAndFlush ensures @CreationTimestamp is populated before response
        entry = journalEntryRepository.saveAndFlush(entry);

        // fire-and-forget async pipeline
        processEntryAsync(entry.getId(), audioBytes, filename, userId);

        return JournalEntryResponse.from(entry, null);
    }

    // transcript-only path: iOS on-device STT, no audio upload
    @Transactional
    public JournalEntryResponse createEntryFromTranscript(UUID userId,
                                                          String transcript,
                                                          OffsetDateTime recordedAt,
                                                          int durationSeconds,
                                                          String idempotencyKey) {
        // Deduplicate: return existing entry if same idempotency key already processed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<JournalEntry> existing = journalEntryRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                JournalEntry e = existing.get();
                AnalysisResult analysis = analysisResultRepository.findByJournalEntryId(e.getId()).orElse(null);
                log.info("Idempotent request — returning existing entry: entryId={} key={}", e.getId(), idempotencyKey);
                return JournalEntryResponse.from(e, analysis);
            }
        }

        if (!entitlementService.consumeQuota(userId, FeatureKey.JOURNAL_ENTRIES)) {
            throw new QuotaExceededException(
                    "JOURNAL_LIMIT",
                    "Monthly journal entry limit reached. Upgrade to Premium for unlimited journaling."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        JournalEntry entry = JournalEntry.builder()
                .user(user)
                .recordedAt(recordedAt)
                .entryDate(recordedAt.toLocalDate())
                .audioDurationSeconds(durationSeconds)
                .transcript(transcript)
                .idempotencyKey(idempotencyKey)
                .status(EntryStatus.ANALYZING)
                .build();

        entry = journalEntryRepository.saveAndFlush(entry);
        analyzeTranscriptAsync(entry.getId(), transcript, userId);

        return JournalEntryResponse.from(entry, null);
    }

    @Async("journalProcessingExecutor")
    public void analyzeTranscriptAsync(UUID entryId, String transcript, UUID userId) {
        log.info("Transcript analysis started: entryId={}", entryId);
        try {
            UserDetails details = getUserDetails(userId);
            AIAnalysisResponse analysis = router.analysis()
                    .analyze(new AIAnalysisRequest(transcript, details.timezone(), details.language()));
            entryUpdater.saveAnalysisResult(entryId, userId, analysis);
            entryUpdater.updateStatus(entryId, EntryStatus.COMPLETE);
            achievementService.checkAndAward(userId);
            log.info("Transcript analysis completed: entryId={}", entryId);
        } catch (Exception e) {
            log.error("Transcript analysis failed: entryId={}", entryId, e);
            entryUpdater.markFailed(entryId, e.getMessage());
            aiJobDlqService.enqueue(
                    entryId, "ANALYSIS", classifyError(e), buildPayload(entryId, userId)
            );
        }
    }

    @Async("journalProcessingExecutor")
    public void processEntryAsync(UUID entryId,
                                  byte[] audioBytes,
                                  String filename,
                                  UUID userId) {
        log.info("Async pipeline started: entryId={}", entryId);

        try {
            // 1. transcribe — bytes stay in memory, never written to disk
            entryUpdater.updateStatus(entryId, EntryStatus.TRANSCRIBING);
            String transcript = router.transcription().transcribe(audioBytes, filename);
            if (transcript == null || transcript.strip().length() < 15) {
                log.warn("Transcript too short ({}), marking failed: entryId={}",
                        transcript == null ? 0 : transcript.length(), entryId);
                entryUpdater.markFailed(entryId,
                        "Audio too short or silent. Please record at least a few seconds of speech.");
                return;
            }
            entryUpdater.setTranscript(entryId, transcript);
            entryUpdater.updateStatus(entryId, EntryStatus.ANALYZING);

            // 2. AI analysis
            UserDetails details = getUserDetails(userId);
            AIAnalysisResponse analysis = router.analysis()
                    .analyze(new AIAnalysisRequest(transcript, details.timezone(), details.language()));

            // 5. save analysis result
            entryUpdater.saveAnalysisResult(entryId, userId, analysis);
            entryUpdater.updateStatus(entryId, EntryStatus.COMPLETE);

            // 6. check achievements
            achievementService.checkAndAward(userId);

            log.info("Async pipeline completed: entryId={}", entryId);
        } catch (Exception e) {
            log.error("Async pipeline failed: entryId={}", entryId, e);
            entryUpdater.markFailed(entryId, e.getMessage());
            aiJobDlqService.enqueue(
                    entryId, "ANALYSIS", classifyError(e), buildPayload(entryId, userId)
            );
        }
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntry(UUID entryId, UUID userId) {
        JournalEntry entry = journalEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));
        AnalysisResult analysis = analysisResultRepository.findByJournalEntryId(entryId).orElse(null);
        return JournalEntryResponse.from(entry, analysis);
    }

    @Transactional(readOnly = true)
    public JournalStatusResponse getStatus(UUID entryId, UUID userId) {
        JournalEntry entry = journalEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));
        return JournalStatusResponse.from(entry);
    }

    // N+1 fix: batch-load analysis results instead of per-entry queries
    @Transactional(readOnly = true)
    public List<JournalEntryResponse> getByDate(UUID userId, LocalDate date) {
        List<JournalEntry> entries = journalEntryRepository
                .findByUserIdAndEntryDateOrderByRecordedAtDesc(userId, date);
        return mapWithAnalysis(entries);
    }

    // uses Pageable instead of hardcoded findTop7
    @Transactional(readOnly = true)
    public List<JournalEntryResponse> getRecent(UUID userId, int limit) {
        List<JournalEntry> entries = journalEntryRepository
                .findByUserIdOrderByRecordedAtDesc(userId, PageRequest.of(0, limit));
        return mapWithAnalysis(entries);
    }

    // batch-loads analysis results in one query to eliminate N+1
    private List<JournalEntryResponse> mapWithAnalysis(List<JournalEntry> entries) {
        if (entries.isEmpty()) return List.of();

        List<UUID> entryIds = entries.stream().map(JournalEntry::getId).toList();
        Map<UUID, AnalysisResult> analysisMap = analysisResultRepository
                .findByJournalEntryIdIn(entryIds)
                .stream()
                .collect(Collectors.toMap(
                        ar -> ar.getJournalEntry().getId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        return entries.stream()
                .map(e -> JournalEntryResponse.from(e, analysisMap.get(e.getId())))
                .toList();
    }


    private UserDetails getUserDetails(UUID userId) {
        return userRepository.findById(userId)
                .map(u -> new UserDetails(u.getTimezone(), u.getPreferredLanguage()))
                .orElse(new UserDetails("UTC", "tr"));
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

    private String buildPayload(UUID entryId, UUID userId) {
        return "{\"entryId\":\"" + entryId + "\",\"userId\":\"" + userId + "\"}";
    }

    private record UserDetails(String timezone, String language) {}
}
