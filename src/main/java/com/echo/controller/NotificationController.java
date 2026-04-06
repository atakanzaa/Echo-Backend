package com.echo.controller;

import com.echo.dto.response.NotificationResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        int boundedSize = Math.min(Math.max(size, 1), 50);
        return ResponseEntity.ok(
                notificationService.getNotifications(
                        principal.getId(),
                        PageRequest.of(Math.max(page, 0), boundedSize)
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
        notificationService.registerPushToken(principal.getId(), request.token(), request.platform());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    private record PushTokenRequest(@NotBlank String token, String platform) {}
}
