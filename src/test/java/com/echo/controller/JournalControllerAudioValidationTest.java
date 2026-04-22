package com.echo.controller;

import com.echo.exception.AudioValidationException;
import com.echo.security.UserPrincipal;
import com.echo.service.JournalService;
import com.echo.service.JournalUploadValidator;
import com.echo.service.UserStatsService;
import com.echo.util.PageableFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

class JournalControllerAudioValidationTest {

    private JournalService journalService;
    private UserStatsService userStatsService;
    private JournalController controller;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        journalService = Mockito.mock(JournalService.class);
        userStatsService = Mockito.mock(UserStatsService.class);
        controller = new JournalController(
                journalService,
                userStatsService,
                new JournalUploadValidator(),
                new PageableFactory()
        );
        principal = Mockito.mock(UserPrincipal.class);
        given(principal.getId()).willReturn(UUID.randomUUID());
    }

    @Test
    void rejectsEmptyAudio() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "empty.m4a", "audio/mp4", new byte[0]);

        assertThatThrownBy(() -> controller.create(
                audio, OffsetDateTime.now().toString(), 10, null, principal))
                .isInstanceOf(AudioValidationException.class)
                .satisfies(ex -> assertThat(((AudioValidationException) ex).getCode())
                        .isEqualTo("AUDIO_EMPTY"));

        Mockito.verifyNoInteractions(journalService);
    }

    @Test
    void rejectsSilentAudioWithLowByteRate() {
        // Reproduces the iOS sim bug: 57 356 bytes for 65 seconds ≈ 880 B/s.
        byte[] bytes = new byte[57_356];
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "silent.m4a", "audio/mp4", bytes);

        assertThatThrownBy(() -> controller.create(
                audio, OffsetDateTime.now().toString(), 65, null, principal))
                .isInstanceOf(AudioValidationException.class)
                .satisfies(ex -> assertThat(((AudioValidationException) ex).getCode())
                        .isEqualTo("AUDIO_TOO_QUIET_OR_SILENT"));

        Mockito.verifyNoInteractions(journalService);
    }

    @Test
    void acceptsHealthyAudio() throws Exception {
        // 200 KB over 10 s ≈ 20 kB/s — well above the 2 kB/s floor.
        byte[] bytes = new byte[200_000];
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "ok.m4a", "audio/mp4", bytes);

        given(journalService.createEntry(
                any(UUID.class), any(byte[].class), any(), any(), any(OffsetDateTime.class),
                anyInt(), eq((String) null))).willReturn(null);

        var response = controller.create(
                audio, OffsetDateTime.now().toString(), 10, null, principal);

        assertThat(response.getStatusCodeValue()).isEqualTo(202);
        Mockito.verify(journalService).createEntry(
                any(UUID.class), any(byte[].class), any(), any(), any(OffsetDateTime.class),
                anyInt(), eq((String) null));
    }
}
