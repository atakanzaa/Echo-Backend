package com.echo.controller;

import com.echo.dto.response.GoalResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.GoalService;
import lombok.RequiredArgsConstructor;
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

    /** Aktif (PENDING) hedefler */
    @GetMapping
    public ResponseEntity<List<GoalResponse>> getActiveGoals(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.getActiveGoals(p.getId()));
    }

    /** Tüm hedefler (COMPLETED, DISMISSED dahil) */
    @GetMapping("/all")
    public ResponseEntity<List<GoalResponse>> getAllGoals(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.getAllGoals(p.getId()));
    }

    /** Hedefi tamamlandı işaretle */
    @PutMapping("/{id}/complete")
    public ResponseEntity<GoalResponse> completeGoal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.completeGoal(p.getId(), id));
    }

    /** Hedefi reddet */
    @PutMapping("/{id}/dismiss")
    public ResponseEntity<GoalResponse> dismissGoal(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(goalService.dismissGoal(p.getId(), id));
    }
}
