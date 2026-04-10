package com.echo.controller;

import com.echo.dto.response.GoalResponse;
import com.echo.dto.response.GoalSuggestionResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.GoalIntegrationService;
import com.echo.service.GoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final GoalIntegrationService goalIntegrationService;

    /** Active goals (PENDING + ACTIVE) */
    @GetMapping
    public ResponseEntity<PagedResponse<GoalResponse>> getActiveGoals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal p) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(goalService.getActiveGoals(p.getId(), pageable));
    }

    /** All goals including COMPLETED and DISMISSED */
    @GetMapping("/all")
    public ResponseEntity<PagedResponse<GoalResponse>> getAllGoals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal p) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(goalService.getAllGoals(p.getId(), pageable));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<GoalSuggestionResponse>> getSuggestions(
            @AuthenticationPrincipal UserPrincipal p,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) UUID journalEntryId,
            @RequestParam(required = false, name = "sessionId") UUID coachSessionId) {
        return ResponseEntity.ok(goalIntegrationService.getSuggestions(
                p.getId(),
                sourceType,
                journalEntryId,
                coachSessionId
        ));
    }

    @PostMapping("/suggestions/{id}/accept")
    public ResponseEntity<GoalResponse> acceptSuggestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalIntegrationService.acceptSuggestion(p.getId(), id));
    }

    @PostMapping("/suggestions/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectSuggestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        goalIntegrationService.rejectSuggestion(p.getId(), id);
    }

    /** Mark goal as completed */
    @PutMapping("/{id}/complete")
    public ResponseEntity<GoalResponse> completeGoal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.completeGoal(p.getId(), id));
    }

    @PutMapping("/{id}/not-completed")
    public ResponseEntity<GoalResponse> markNotCompleted(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.markNotCompleted(p.getId(), id));
    }

    /** Dismiss goal */
    @PutMapping("/{id}/dismiss")
    public ResponseEntity<GoalResponse> dismissGoal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.dismissGoal(p.getId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        goalService.deleteGoal(p.getId(), id);
    }
}
