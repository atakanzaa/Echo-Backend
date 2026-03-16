package com.echo.service;

import com.echo.domain.journal.EntryStatus;
import com.echo.domain.journal.JournalEntry;
import com.echo.domain.user.User;
import com.echo.dto.response.JournalEntryResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.AnalysisResultRepository;
import com.echo.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    @Mock StorageService           storageService;
    @Mock AchievementService       achievementService;

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
        given(journalEntryRepository.findById(entry.getId())).willReturn(Optional.of(entry));
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
        given(journalEntryRepository.findById(entry.getId())).willReturn(Optional.of(entry));

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
        given(analysisResultRepository.findByJournalEntryId(entry.getId())).willReturn(Optional.empty());

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
        given(journalEntryRepository.findTop7ByUserIdOrderByRecordedAtDesc(userId)).willReturn(List.of(e1, e2));
        given(analysisResultRepository.findByJournalEntryId(any())).willReturn(Optional.empty());

        // when
        var responses = journalService.getRecent(userId, 5);

        // then
        assertThat(responses).hasSize(2);
    }
}
