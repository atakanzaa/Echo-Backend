package com.echo.controller;

import com.echo.dto.request.UpdateProfileRequest;
import com.echo.dto.response.DetailedStatsResponse;
import com.echo.dto.response.ProfileSummaryResponse;
import com.echo.dto.response.UserResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.UserRepository;
import com.echo.security.UserPrincipal;
import com.echo.service.UserStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final UserStatsService userStatsService;

    @GetMapping("/me/stats")
    public ResponseEntity<UserResponse> getStats(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
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
    @Transactional
    public ResponseEntity<UserResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        return userRepository.findById(principal.getId())
                .map(user -> {
                    if (request.displayName() != null) user.setDisplayName(request.displayName());
                    if (request.timezone() != null)    user.setTimezone(request.timezone());
                    if (request.language() != null)    user.setPreferredLanguage(request.language());
                    return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
                })
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
