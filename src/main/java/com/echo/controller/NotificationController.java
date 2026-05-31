package com.echo.controller;

import com.echo.dto.response.NotificationPreferencesResponse;
import com.echo.dto.response.NotificationResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.NotificationService;
import com.echo.service.notification.NotificationPreferenceService;
import com.echo.util.PageableFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;
    private final PageableFactory pageableFactory;

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                notificationService.getNotifications(
                        principal.getId(),
                        pageableFactory.create(page, size)
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(principal.getId())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAsRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push-token")
    public ResponseEntity<Void> registerPushToken(
            @Valid @RequestBody PushTokenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.registerPushToken(
                principal.getId(),
                request.token(),
                request.platform(),
                request.environment());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/push-token")
    public ResponseEntity<Void> unregisterPushToken(@Valid @RequestBody UnregisterPushTokenRequest request) {
        notificationService.deactivatePushToken(request.token());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> getPreferences(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                NotificationPreferencesResponse.from(preferenceService.getOrCreate(principal.getId()))
        );
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody NotificationPreferencesUpdateRequest body) {
        var patch = new NotificationPreferenceService.NotificationPreferencePatch(
                body.masterEnabled(),
                body.dailyRemindersEnabled(),
                body.communityEnabled(),
                body.achievementsEnabled(),
                body.insightsEnabled(),
                body.moodAlertsEnabled(),
                body.capsulesEnabled(),
                body.coachEnabled(),
                body.systemEnabled(),
                body.preferredLocalHour(),
                body.quietHoursStartLocal(),
                body.quietHoursEndLocal(),
                body.timezone()
        );
        return ResponseEntity.ok(
                NotificationPreferencesResponse.from(preferenceService.update(principal.getId(), patch))
        );
    }

    private record PushTokenRequest(@NotBlank String token, String platform, String environment) {}

    private record UnregisterPushTokenRequest(@NotBlank String token) {}

    private record NotificationPreferencesUpdateRequest(
            Boolean masterEnabled,
            Boolean dailyRemindersEnabled,
            Boolean communityEnabled,
            Boolean achievementsEnabled,
            Boolean insightsEnabled,
            Boolean moodAlertsEnabled,
            Boolean capsulesEnabled,
            Boolean coachEnabled,
            Boolean systemEnabled,
            Integer preferredLocalHour,
            Integer quietHoursStartLocal,
            Integer quietHoursEndLocal,
            String timezone
    ) {}
}
