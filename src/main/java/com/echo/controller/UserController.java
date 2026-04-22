package com.echo.controller;

import com.echo.dto.request.UpdateProfileRequest;
import com.echo.dto.response.DetailedStatsResponse;
import com.echo.dto.response.ProfileSummaryResponse;
import com.echo.dto.response.UserResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.UserService;
import com.echo.service.UserStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserStatsService userStatsService;

    @GetMapping("/me/stats")
    public ResponseEntity<UserResponse> getStats(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getCurrentUser(principal.getId()));
    }

    @GetMapping("/me/profile-summary")
    public ResponseEntity<ProfileSummaryResponse> getProfileSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        ProfileSummaryResponse response = userStatsService.getProfileSummary(principal.getId());
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/stats/detailed")
    public ResponseEntity<DetailedStatsResponse> getDetailedStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userStatsService.getDetailedStats(principal.getId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(userService.updateProfile(principal.getId(), request));
    }
}
