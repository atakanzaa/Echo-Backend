package com.echo.controller;

import com.echo.dto.request.CreateCoachSessionRequest;
import com.echo.dto.request.SendCoachMessageRequest;
import com.echo.dto.response.CoachMessageResponse;
import com.echo.dto.response.CoachSessionResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.CoachService;
import com.echo.util.PageableFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coach")
@RequiredArgsConstructor
public class CoachController {
    private final CoachService coachService;
    private final PageableFactory pageableFactory;

    @GetMapping("/sessions")
    public ResponseEntity<PagedResponse<CoachSessionResponse>> getSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal p) {
        Pageable pageable = pageableFactory.create(page, size);
        return ResponseEntity.ok(coachService.getSessions(p.getId(), pageable));
    }

    @PostMapping("/sessions")
    public ResponseEntity<CoachSessionResponse> createSession(
            @RequestBody(required = false) CreateCoachSessionRequest request,
            @AuthenticationPrincipal UserPrincipal p) {
        UUID journalEntryId = request != null ? request.journalEntryId() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachService.createSession(p.getId(), journalEntryId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<PagedResponse<CoachMessageResponse>> getMessages(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal p) {
        Pageable pageable = pageableFactory.create(page, size);
        return ResponseEntity.ok(coachService.getMessages(sessionId, p.getId(), pageable));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<CoachMessageResponse>> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendCoachMessageRequest request,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachService.sendMessage(sessionId, p.getId(), request));
    }

    /** iOS calls this when the user leaves the coach screen — soft-close + async memory update */
    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<Void> endSession(
            @PathVariable UUID sessionId, @AuthenticationPrincipal UserPrincipal p) {
        coachService.endSession(sessionId, p.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId, @AuthenticationPrincipal UserPrincipal p) {
        coachService.deleteSession(sessionId, p.getId());
        return ResponseEntity.noContent().build();
    }
}
