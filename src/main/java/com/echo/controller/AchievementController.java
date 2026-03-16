package com.echo.controller;

import com.echo.dto.response.AchievementsResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public ResponseEntity<AchievementsResponse> getAchievements(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(achievementService.getAchievements(principal.getId()));
    }
}
