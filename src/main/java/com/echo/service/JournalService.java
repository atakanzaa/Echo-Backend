package com.echo.service;

import com.echo.ai.AITranscriptionRequest;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");

    private final JournalEntryRepository journalEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository userRepository;
    private final JournalProcessingWorker journalProcessingWorker;
    private final EntitlementService entitlementService;

    public JournalEntryResponse createEntry(UUID userId,
                                            byte[] audioBytes,
                                            String filename,
                                            String contentType,
                                            OffsetDateTime recordedAt,
                                            int durationSeconds,
                                            String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<JournalEntryResponse> existing = findExistingIdempotentResponse(userId, normalizedKey);
        if (existing.isPresent()) {
            return existing.get();
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
                .idempotencyKey(normalizedKey)
                .status(EntryStatus.UPLOADING)
                .build();

        try {
            entry = journalEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            return recoverIdempotencyRace(userId, normalizedKey, ex);
        }

        journalProcessingWorker.processAudioEntryAsync(
                entry.getId(),
                new AITranscriptionRequest(audioBytes, filename, contentType, durationSeconds),
                userId
        );

        return JournalEntryResponse.from(entry, null);
    }

    public JournalEntryResponse createEntryFromTranscript(UUID userId,
                                                          String transcript,
                                                          OffsetDateTime recordedAt,
                                                          int durationSeconds,
                                                          String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        Optional<JournalEntryResponse> existing = findExistingIdempotentResponse(userId, normalizedKey);
        if (existing.isPresent()) {
            return existing.get();
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
                .idempotencyKey(normalizedKey)
                .status(EntryStatus.ANALYZING)
                .build();

        try {
            entry = journalEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            return recoverIdempotencyRace(userId, normalizedKey, ex);
        }
        journalProcessingWorker.analyzeTranscriptAsync(entry.getId(), transcript, userId);

        return JournalEntryResponse.from(entry, null);
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

    private Optional<JournalEntryResponse> findExistingIdempotentResponse(UUID userId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return journalEntryRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(entry -> {
                    AnalysisResult analysis = analysisResultRepository.findByJournalEntryId(entry.getId()).orElse(null);
                    log.info("Idempotent journal request resolved to existing entry: entryId={}", entry.getId());
                    return JournalEntryResponse.from(entry, analysis);
                });
    }

    private JournalEntryResponse recoverIdempotencyRace(UUID userId,
                                                        String idempotencyKey,
                                                        DataIntegrityViolationException ex) {
        if (idempotencyKey == null) {
            throw ex;
        }
        entitlementService.refundQuota(userId, FeatureKey.JOURNAL_ENTRIES);
        return findExistingIdempotentResponse(userId, idempotencyKey)
                .orElseThrow(() -> ex);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                || !IDEMPOTENCY_KEY_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Idempotency key must be 1-64 characters using letters, digits, '.', '_', ':', or '-'");
        }
        return normalized;
    }
}
