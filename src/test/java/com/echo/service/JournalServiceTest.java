package com.echo.service;

import com.echo.ai.AITranscriptionRequest;
import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.response.JournalEntryResponse;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock JournalEntryRepository  journalEntryRepository;
    @Mock AnalysisResultRepository analysisResultRepository;
    @Mock UserRepository           userRepository;
    @Mock JournalProcessingWorker  journalProcessingWorker;
    @Mock EntitlementService       entitlementService;

    @InjectMocks JournalService journalService;

    private JournalEntry buildEntry(UUID userId) {
        User user = User.builder().id(userId).build();
        return JournalEntry.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status(EntryStatus.COMPLETE)
                .recordedAt(OffsetDateTime.now())
                .entryDate(LocalDate.now())
                .audioDurationSeconds(120)
                .build();
    }

    @Test
    void getEntry_withValidIdAndOwner_returnsResponse() {
        // given
        UUID userId  = UUID.randomUUID();
        JournalEntry entry = buildEntry(userId);
        given(journalEntryRepository.findByIdAndUserId(entry.getId(), userId)).willReturn(Optional.of(entry));
        given(analysisResultRepository.findByJournalEntryId(entry.getId())).willReturn(Optional.empty());

        // when
        JournalEntryResponse response = journalService.getEntry(entry.getId(), userId);

        // then
        assertThat(response.id()).isEqualTo(entry.getId());
        assertThat(response.status()).isEqualTo("complete");
    }

    @Test
    void getEntry_withDifferentOwner_throwsResourceNotFoundException() {
        // given
        UUID userId  = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        JournalEntry entry = buildEntry(ownerId);
        given(journalEntryRepository.findByIdAndUserId(entry.getId(), userId)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> journalService.getEntry(entry.getId(), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByDate_returnsEntriesForDate() {
        // given
        UUID userId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        JournalEntry entry = buildEntry(userId);
        given(journalEntryRepository.findByUserIdAndEntryDateOrderByRecordedAtDesc(userId, date)).willReturn(List.of(entry));
        given(analysisResultRepository.findByJournalEntryIdIn(List.of(entry.getId()))).willReturn(List.of());

        // when
        var responses = journalService.getByDate(userId, date);

        // then
        assertThat(responses).hasSize(1);
    }

    @Test
    void getRecent_returnsLimitedEntries() {
        // given
        UUID userId = UUID.randomUUID();
        JournalEntry e1 = buildEntry(userId);
        JournalEntry e2 = buildEntry(userId);
        given(journalEntryRepository.findByUserIdOrderByRecordedAtDesc(eq(userId), any(Pageable.class))).willReturn(List.of(e1, e2));
        given(analysisResultRepository.findByJournalEntryIdIn(List.of(e1.getId(), e2.getId()))).willReturn(List.of());

        // when
        var responses = journalService.getRecent(userId, 5);

        // then
        assertThat(responses).hasSize(2);
    }

    @Test
    void createEntry_dispatchesAudioProcessingWorker() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        byte[] audio = new byte[] {1, 2, 3, 4};
        OffsetDateTime recordedAt = OffsetDateTime.now();

        given(entitlementService.consumeQuota(userId, com.echo.domain.subscription.FeatureKey.JOURNAL_ENTRIES))
                .willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(journalEntryRepository.saveAndFlush(any(JournalEntry.class))).willAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        JournalEntryResponse response = journalService.createEntry(
                userId,
                audio,
                "voice.m4a",
                "audio/mp4",
                recordedAt,
                18,
                null
        );

        ArgumentCaptor<AITranscriptionRequest> requestCaptor = ArgumentCaptor.forClass(AITranscriptionRequest.class);
        then(journalProcessingWorker).should()
                .processAudioEntryAsync(eq(response.id()), requestCaptor.capture(), eq(userId));
        AITranscriptionRequest request = requestCaptor.getValue();
        assertThat(request.filename()).isEqualTo("voice.m4a");
        assertThat(request.contentType()).isEqualTo("audio/mp4");
        assertThat(request.durationSeconds()).isEqualTo(18);
        assertThat(Arrays.equals(request.audioBytes(), audio)).isTrue();
    }

    @Test
    void createEntryFromTranscript_dispatchesTranscriptWorker() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        OffsetDateTime recordedAt = OffsetDateTime.now();

        given(entitlementService.consumeQuota(userId, com.echo.domain.subscription.FeatureKey.JOURNAL_ENTRIES))
                .willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(journalEntryRepository.saveAndFlush(any(JournalEntry.class))).willAnswer(invocation -> {
            JournalEntry entry = invocation.getArgument(0);
            entry.setId(UUID.randomUUID());
            return entry;
        });

        JournalEntryResponse response = journalService.createEntryFromTranscript(
                userId,
                "Bugun kendimi daha iyi hissediyorum.",
                recordedAt,
                12,
                null
        );

        then(journalProcessingWorker).should()
                .analyzeTranscriptAsync(eq(response.id()), eq("Bugun kendimi daha iyi hissediyorum."), eq(userId));
    }

    @Test
    void createEntry_throwsWhenQuotaIsExceeded() {
        UUID userId = UUID.randomUUID();
        given(entitlementService.consumeQuota(userId, com.echo.domain.subscription.FeatureKey.JOURNAL_ENTRIES))
                .willReturn(false);

        assertThatThrownBy(() -> journalService.createEntry(
                userId,
                new byte[] {1},
                "voice.m4a",
                "audio/mp4",
                OffsetDateTime.now(),
                10,
                null
        )).isInstanceOf(QuotaExceededException.class);

        then(journalProcessingWorker).shouldHaveNoInteractions();
    }

    @Test
    void createEntry_returnsExistingEntryForSameUserIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        String key = "upload-123";
        JournalEntry existing = buildEntry(userId);
        given(journalEntryRepository.findByUserIdAndIdempotencyKey(userId, key))
                .willReturn(Optional.of(existing));
        given(analysisResultRepository.findByJournalEntryId(existing.getId())).willReturn(Optional.empty());

        JournalEntryResponse response = journalService.createEntry(
                userId,
                new byte[] {1, 2, 3},
                "voice.m4a",
                "audio/mp4",
                OffsetDateTime.now(),
                10,
                key
        );

        assertThat(response.id()).isEqualTo(existing.getId());
        then(entitlementService).should(never()).consumeQuota(any(), any());
        then(journalProcessingWorker).shouldHaveNoInteractions();
    }

    @Test
    void createEntry_duplicateKeyRaceRefundsQuotaAndReturnsUserScopedEntry() {
        UUID userId = UUID.randomUUID();
        String key = "race-key";
        User user = User.builder().id(userId).build();
        JournalEntry existing = buildEntry(userId);

        given(journalEntryRepository.findByUserIdAndIdempotencyKey(userId, key))
                .willReturn(Optional.empty(), Optional.of(existing));
        given(entitlementService.consumeQuota(userId, FeatureKey.JOURNAL_ENTRIES)).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(journalEntryRepository.saveAndFlush(any(JournalEntry.class)))
                .willThrow(new DataIntegrityViolationException("duplicate idempotency key"));
        given(analysisResultRepository.findByJournalEntryId(existing.getId())).willReturn(Optional.empty());

        JournalEntryResponse response = journalService.createEntry(
                userId,
                new byte[] {1, 2, 3},
                "voice.m4a",
                "audio/mp4",
                OffsetDateTime.now(),
                10,
                key
        );

        assertThat(response.id()).isEqualTo(existing.getId());
        then(entitlementService).should().refundQuota(userId, FeatureKey.JOURNAL_ENTRIES);
        then(journalProcessingWorker).shouldHaveNoInteractions();
    }

    @Test
    void createEntry_rejectsInvalidIdempotencyKey() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> journalService.createEntry(
                userId,
                new byte[] {1, 2, 3},
                "voice.m4a",
                "audio/mp4",
                OffsetDateTime.now(),
                10,
                "bad key!"
        )).isInstanceOf(IllegalArgumentException.class);

        then(entitlementService).shouldHaveNoInteractions();
    }
}
