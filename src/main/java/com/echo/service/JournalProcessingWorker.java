package com.echo.service;

import com.echo.ai.AIAnalysisRequest;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.ai.AITranscriptionRequest;
import com.echo.ai.AITranscriptionResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.TranscriptionFailedException;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalProcessingWorker {

    private static final int MIN_TRANSCRIPT_CHARS = 15;

    private final UserRepository userRepository;
    private final AIProviderRouter router;
    private final AchievementService achievementService;
    private final AiJobDlqService aiJobDlqService;
    private final JournalEntryUpdater entryUpdater;

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
            entryUpdater.markFailed(entryId, userFacingAnalysisFailure(e));
            aiJobDlqService.enqueue(
                    entryId, "ANALYSIS", classifyError(e), buildPayload(entryId, userId)
            );
        }
    }

    @Async("journalProcessingExecutor")
    public void processAudioEntryAsync(UUID entryId, AITranscriptionRequest request, UUID userId) {
        long bytesPerSecond = request.durationSeconds() > 0
                ? request.audioBytes().length / request.durationSeconds()
                : request.audioBytes().length;
        log.info("Async pipeline started: entryId={} bytes={} durationSeconds={} bytesPerSecond={} contentType={}",
                entryId, request.audioBytes().length, request.durationSeconds(), bytesPerSecond, request.contentType());

        String transcript;
        try {
            entryUpdater.updateStatus(entryId, EntryStatus.TRANSCRIBING);
            AITranscriptionResult result = router.transcription().transcribe(request);
            transcript = result.cleanedTranscript();

            if (transcript.length() < MIN_TRANSCRIPT_CHARS) {
                throw new TranscriptionFailedException(
                        "TRANSCRIPT_TOO_SHORT",
                        "Ses kaydı çok kısa ya da sessiz görünüyor. Lütfen birkaç saniyelik net bir kayıtla tekrar dene."
                );
            }

            log.info("Transcription accepted: entryId={} provider={} mimeType={} promptTokens={} candidateTokens={} transcriptChars={}",
                    entryId,
                    result.provider(),
                    result.resolvedMimeType(),
                    result.promptTokenCount(),
                    result.candidateTokenCount(),
                    transcript.length());

            entryUpdater.setTranscript(entryId, transcript);
            entryUpdater.updateStatus(entryId, EntryStatus.ANALYZING);
        } catch (TranscriptionFailedException e) {
            log.warn("Transcription rejected: entryId={} code={} message={}", entryId, e.getCode(), e.getUserMessage());
            entryUpdater.markFailed(entryId, e.getUserMessage());
            return;
        } catch (ServiceUnavailableException e) {
            log.warn("Transcription unavailable: entryId={} message={}", entryId, e.getMessage());
            entryUpdater.markFailed(entryId,
                    "Ses tanıma servisi şu anda geçici olarak kullanılamıyor. Lütfen biraz sonra tekrar dene.");
            return;
        } catch (Exception e) {
            log.error("Transcription failed unexpectedly: entryId={}", entryId, e);
            entryUpdater.markFailed(entryId,
                    "Ses kaydı çözümlenemedi. Lütfen daha net bir kayıtla tekrar dene.");
            return;
        }

        try {
            UserDetails details = getUserDetails(userId);
            AIAnalysisResponse analysis = router.analysis()
                    .analyze(new AIAnalysisRequest(transcript, details.timezone(), details.language()));

            entryUpdater.saveAnalysisResult(entryId, userId, analysis);
            entryUpdater.updateStatus(entryId, EntryStatus.COMPLETE);
            achievementService.checkAndAward(userId);
            log.info("Async pipeline completed: entryId={}", entryId);
        } catch (Exception e) {
            log.error("Analysis failed after successful transcription: entryId={}", entryId, e);
            entryUpdater.markFailed(entryId, userFacingAnalysisFailure(e));
            aiJobDlqService.enqueue(
                    entryId, "ANALYSIS", classifyError(e), buildPayload(entryId, userId)
            );
        }
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

    private String userFacingAnalysisFailure(Exception e) {
        if (e instanceof ServiceUnavailableException) {
            return "Yapay zeka analizi şu anda geçici olarak kullanılamıyor. Lütfen biraz sonra tekrar dene.";
        }
        return "Ses kaydı alındı ancak analiz tamamlanamadı. Lütfen tekrar dene.";
    }

    private record UserDetails(String timezone, String language) {}
}
