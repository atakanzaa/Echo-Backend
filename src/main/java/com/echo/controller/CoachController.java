package com.echo.controller;

import com.echo.dto.request.CreateCoachSessionRequest;
import com.echo.dto.request.SendCoachMessageRequest;
import com.echo.dto.response.CoachMessageResponse;
import com.echo.dto.response.CoachSessionResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.CoachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/sessions")
    public ResponseEntity<List<CoachSessionResponse>> getSessions(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(coachService.getSessions(p.getId()));
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
    public ResponseEntity<List<CoachMessageResponse>> getMessages(
            @PathVariable UUID sessionId, @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(coachService.getMessages(sessionId, p.getId()));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<CoachMessageResponse>> sendMessage(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SendCoachMessageRequest request,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachService.sendMessage(sessionId, p.getId(), request));
    }

    /** iOS, kullanıcı coach ekranından ayrıldığında çağırır — soft-close + async bellek güncelleme */
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
