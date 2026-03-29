package com.echo.controller;

import com.echo.dto.request.CreateJournalFromTranscriptRequest;
import com.echo.dto.response.JournalEntryResponse;
import com.echo.dto.response.JournalStatusResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journal/entries")
@RequiredArgsConstructor
public class JournalController {

    private static final Logger log = LoggerFactory.getLogger(JournalController.class);
    private final JournalService journalService;

    /**
     * Upload audio and start async pipeline.
     * Returns 202 Accepted immediately; client polls /status for progress.
     * Idempotency-Key header deduplicates retries on bad network.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<JournalEntryResponse> create(
            @RequestParam("audio")           MultipartFile audio,
            @RequestParam("recordedAt")      String recordedAt,
            @RequestParam("durationSeconds") int durationSeconds,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        byte[] audioBytes = audio.getBytes();
        log.info("Audio upload received: filename={} bytes={} durationSeconds={}",
                audio.getOriginalFilename(), audioBytes.length, durationSeconds);
        if (audioBytes.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        JournalEntryResponse response = journalService.createEntry(
                principal.getId(),
                audioBytes,
                audio.getOriginalFilename(),
                OffsetDateTime.parse(recordedAt),
                durationSeconds,
                idempotencyKey
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Transcript-only path — iOS on-device Apple Speech STT result.
     * Audio never reaches the server: privacy-preserving, lower bandwidth.
     * Starts directly at ANALYZING status.
     */
    @PostMapping(value = "/transcript", consumes = "application/json")
    public ResponseEntity<JournalEntryResponse> createFromTranscript(
            @Valid @RequestBody CreateJournalFromTranscriptRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        JournalEntryResponse response = journalService.createEntryFromTranscript(
                principal.getId(),
                request.transcript(),
                OffsetDateTime.parse(request.recordedAt()),
                request.durationSeconds(),
                request.idempotencyKey()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponse> getEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(journalService.getEntry(id, principal.getId()));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<JournalStatusResponse> getStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(journalService.getStatus(id, principal.getId()));
    }

    @GetMapping
    public ResponseEntity<List<JournalEntryResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(journalService.getByDate(principal.getId(), date));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<JournalEntryResponse>> getRecent(
            @RequestParam(defaultValue = "7") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(journalService.getRecent(principal.getId(), Math.min(limit, 50)));
    }
}
