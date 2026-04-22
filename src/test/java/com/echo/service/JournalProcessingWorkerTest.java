package com.echo.service;

import com.echo.ai.AIAnalysisProvider;
import com.echo.ai.AIAnalysisRequest;
import com.echo.ai.AIAnalysisResponse;
import com.echo.ai.AIProviderRouter;
import com.echo.ai.AITranscriptionProvider;
import com.echo.ai.AITranscriptionRequest;
import com.echo.ai.AITranscriptionResult;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.user.User;
import com.echo.exception.TranscriptionFailedException;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JournalProcessingWorkerTest {

    @Mock UserRepository userRepository;
    @Mock AIProviderRouter router;
    @Mock AchievementService achievementService;
    @Mock AiJobDlqService aiJobDlqService;
    @Mock JournalEntryUpdater entryUpdater;
    @Mock AITranscriptionProvider transcriptionProvider;
    @Mock AIAnalysisProvider analysisProvider;

    private JournalProcessingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new JournalProcessingWorker(
                userRepository,
                router,
                achievementService,
                aiJobDlqService,
                entryUpdater
        );
        given(router.transcription()).willReturn(transcriptionProvider);
    }

    @Test
    void transcriptionFailureMarksEntryFailedWithoutEnqueuingDlq() {
        UUID entryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AITranscriptionRequest request = new AITranscriptionRequest(new byte[57_356], "voice.m4a", "audio/mp4", 18);

        given(transcriptionProvider.transcribe(request)).willThrow(
                new TranscriptionFailedException("TRANSCRIPTION_AUDIO_NOT_PARSED", "Ses kaydı çözümlenemedi.")
        );

        worker.processAudioEntryAsync(entryId, request, userId);

        verify(entryUpdater).updateStatus(entryId, EntryStatus.TRANSCRIBING);
        verify(entryUpdater).markFailed(entryId, "Ses kaydı çözümlenemedi.");
        verify(aiJobDlqService, never()).enqueue(any(), any(), any(), any());
        verify(router, never()).analysis();
    }

    @Test
    void analysisFailureEnqueuesAnalysisDlqAfterTranscriptIsSaved() {
        UUID entryId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AITranscriptionRequest request = new AITranscriptionRequest(new byte[200_000], "voice.m4a", "audio/mp4", 18);
        User user = User.builder().id(userId).timezone("Europe/Istanbul").preferredLanguage("tr").build();

        given(router.analysis()).willReturn(analysisProvider);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(transcriptionProvider.transcribe(request)).willReturn(
                new AITranscriptionResult("Bugun kendimi iyi hissediyorum.", "gemini", 160, 20, "audio/mp4", List.of())
        );
        given(analysisProvider.analyze(any(AIAnalysisRequest.class)))
                .willThrow(new RuntimeException("request timeout"));

        worker.processAudioEntryAsync(entryId, request, userId);

        verify(entryUpdater).updateStatus(entryId, EntryStatus.TRANSCRIBING);
        verify(entryUpdater).setTranscript(entryId, "Bugun kendimi iyi hissediyorum.");
        verify(entryUpdater).updateStatus(entryId, EntryStatus.ANALYZING);
        verify(entryUpdater).markFailed(eq(entryId), contains("analiz"));
        verify(aiJobDlqService).enqueue(eq(entryId), eq("ANALYSIS"), eq("TIMEOUT"), any());
    }
}
