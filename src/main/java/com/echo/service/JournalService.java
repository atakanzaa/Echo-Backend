package com.echo.service;

import com.echo.ai.AIAnalysisRequest;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.domain.journal.AnalysisResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import com.echo.dto.response.JournalEntryResponse;
import com.echo.dto.response.JournalStatusResponse;
import com.echo.event.JournalAnalysisCompletedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntryRepository  journalEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final UserRepository           userRepository;
    private final StorageService           storageService;
    private final AIProviderRouter         router;
    private final AchievementService       achievementService;
    private final ApplicationEventPublisher eventPublisher;

    // MARK: — Create Entry (sync — anında 202 döner)

    @Transactional
    public JournalEntryResponse createEntry(UUID userId,
                                            byte[] audioBytes,
                                            String filename,
                                            OffsetDateTime recordedAt,
                                            int durationSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

        JournalEntry entry = JournalEntry.builder()
                .user(user)
                .recordedAt(recordedAt)
                .entryDate(recordedAt.toLocalDate())
                .audioDurationSeconds(durationSeconds)
                .status(EntryStatus.UPLOADING)
                .build();

        // saveAndFlush: INSERT hemen çalışır → @CreationTimestamp null kalmaz
        entry = journalEntryRepository.saveAndFlush(entry);

        // Async pipeline başlat — bu thread beklemez
        processEntryAsync(entry.getId(), audioBytes, filename, userId);

        return JournalEntryResponse.from(entry, null);
    }

    /**
     * Transcript-only path — iOS Apple Speech STT sonucunu doğrudan alır.
     * Audio sunucuya gelmez (gizlilik). UPLOADING ve TRANSCRIBING adımları atlanır.
     */
    @Transactional
    public JournalEntryResponse createEntryFromTranscript(UUID userId,
                                                          String transcript,
                                                          OffsetDateTime recordedAt,
                                                          int durationSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

        JournalEntry entry = JournalEntry.builder()
                .user(user)
                .recordedAt(recordedAt)
                .entryDate(recordedAt.toLocalDate())
                .audioDurationSeconds(durationSeconds)
                .transcript(transcript)
                .status(EntryStatus.ANALYZING)
                .build();

        // saveAndFlush: INSERT hemen çalışır → @CreationTimestamp null kalmaz
        entry = journalEntryRepository.saveAndFlush(entry);
        analyzeTranscriptAsync(entry.getId(), transcript, userId);

        return JournalEntryResponse.from(entry, null);
    }

    @Async("journalProcessingExecutor")
    public CompletableFuture<Void> analyzeTranscriptAsync(UUID entryId, String transcript, UUID userId) {
        log.info("Transcript analiz başladı: entryId={}", entryId);
        try {
            String timezone = getUserTimezone(userId);
            AIAnalysisResponse analysis = router.analysis()
                    .analyze(new AIAnalysisRequest(transcript, timezone));
            saveAnalysisResult(entryId, userId, analysis);
            updateStatus(entryId, EntryStatus.COMPLETE);
            achievementService.checkAndAward(userId);
            log.info("Transcript analiz tamamlandı: entryId={}", entryId);
        } catch (Exception e) {
            log.error("Transcript analiz hatası: entryId={}", entryId, e);
            markFailed(entryId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    // MARK: — Async Pipeline

    @Async("journalProcessingExecutor")
    public CompletableFuture<Void> processEntryAsync(UUID entryId,
                                                     byte[] audioBytes,
                                                     String filename,
                                                     UUID userId) {
        log.info("Async pipeline başladı: entryId={}", entryId);

        try {
            // 1. Ses dosyasını sakla
            updateStatus(entryId, EntryStatus.TRANSCRIBING);
            String audioUrl = storageService.save(audioBytes, filename);
            setAudioUrl(entryId, audioUrl);

            // 2. Transkripsiyon
            String transcript = router.transcription().transcribe(audioBytes, filename);
            setTranscript(entryId, transcript);
            updateStatus(entryId, EntryStatus.ANALYZING);

            // 3. Ses dosyasını hemen sil — gizlilik
            storageService.delete(audioUrl);
            clearAudioUrl(entryId);

            // 4. AI Analizi
            String timezone = getUserTimezone(userId);
            AIAnalysisResponse analysis = router.analysis()
                    .analyze(new AIAnalysisRequest(transcript, timezone));

            // 5. Analiz sonucunu kaydet
            saveAnalysisResult(entryId, userId, analysis);
            updateStatus(entryId, EntryStatus.COMPLETE);

            // 6. Achievement kontrolü
            achievementService.checkAndAward(userId);

            log.info("Async pipeline tamamlandı: entryId={}", entryId);

        } catch (Exception e) {
            log.error("Async pipeline hatası: entryId={}", entryId, e);
            markFailed(entryId, e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    // MARK: — Read Operations

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntry(UUID entryId, UUID userId) {
        JournalEntry entry = journalEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Günlük girişi bulunamadı"));
        AnalysisResult analysis = analysisResultRepository.findByJournalEntryId(entryId).orElse(null);
        return JournalEntryResponse.from(entry, analysis);
    }

    @Transactional(readOnly = true)
    public JournalStatusResponse getStatus(UUID entryId, UUID userId) {
        JournalEntry entry = journalEntryRepository.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Günlük girişi bulunamadı"));
        return JournalStatusResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> getByDate(UUID userId, LocalDate date) {
        return journalEntryRepository
                .findByUserIdAndEntryDateOrderByRecordedAtDesc(userId, date)
                .stream()
                .map(e -> {
                    AnalysisResult analysis = analysisResultRepository
                            .findByJournalEntryId(e.getId()).orElse(null);
                    return JournalEntryResponse.from(e, analysis);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> getRecent(UUID userId, int limit) {
        return journalEntryRepository
                .findTop7ByUserIdOrderByRecordedAtDesc(userId)
                .stream()
                .limit(limit)
                .map(e -> {
                    AnalysisResult analysis = analysisResultRepository
                            .findByJournalEntryId(e.getId()).orElse(null);
                    return JournalEntryResponse.from(e, analysis);
                })
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    @Transactional
    protected void updateStatus(UUID entryId, EntryStatus status) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(status);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    protected void setAudioUrl(UUID entryId, String audioUrl) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setAudioUrl(audioUrl);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    protected void clearAudioUrl(UUID entryId) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setAudioUrl(null);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    protected void setTranscript(UUID entryId, String transcript) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setTranscript(transcript);
            journalEntryRepository.save(e);
        });
    }

    @Transactional
    protected void saveAnalysisResult(UUID entryId, UUID userId, AIAnalysisResponse analysis) {
        JournalEntry entry = journalEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Entry bulunamadı"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));

        AnalysisResult result = AnalysisResult.builder()
                .journalEntry(entry)
                .user(user)
                .entryDate(entry.getEntryDate())
                .summary(analysis.summary())
                .moodScore(BigDecimal.valueOf(analysis.moodScore()))
                .moodLabel(analysis.moodLabel())
                .topics(analysis.topics())
                .reflectiveQuestion(analysis.reflectiveQuestion())
                .keyEmotions(analysis.keyEmotions())
                .energyLevel(analysis.energyLevel())
                .rawAiResponse(analysis.rawJson())
                .aiProvider(router.activeProvider())
                .build();

        analysisResultRepository.save(result);

        // Domain event yayınla — GoalEventListener + TimeCapsuleEventListener dinler
        eventPublisher.publishEvent(new JournalAnalysisCompletedEvent(userId, entryId, analysis));

        // User mood average güncelle
        updateUserMoodAverage(userId);
    }

    @Transactional
    protected void markFailed(UUID entryId, String errorMessage) {
        journalEntryRepository.findById(entryId).ifPresent(e -> {
            e.setStatus(EntryStatus.FAILED);
            e.setErrorMessage(errorMessage);
            journalEntryRepository.save(e);
        });
    }

    private String getUserTimezone(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getTimezone)
                .orElse("UTC");
    }

    @Transactional
    private void updateUserMoodAverage(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            var results = analysisResultRepository
                    .findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                            userId,
                            LocalDate.now().minusDays(30),
                            LocalDate.now()
                    );
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
}
