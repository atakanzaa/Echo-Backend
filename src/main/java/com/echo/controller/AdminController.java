package com.echo.controller;

import com.echo.domain.notification.NotificationType;
import com.echo.dto.request.AIConfigRequest;
import com.echo.security.UserPrincipal;
import com.echo.service.AdminService;
import com.echo.service.NotificationService;
import com.echo.service.notification.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;

    @GetMapping("/ai-config")
    public ResponseEntity<Map<String, String>> getAIConfig() {
        return ResponseEntity.ok(Map.of(
                "provider", adminService.getActiveProvider(),
                "transcriptionProvider", adminService.getActiveTranscriptionProvider()
        ));
    }

    @PostMapping("/ai-config")
    public ResponseEntity<Map<String, String>> updateAIConfig(@Valid @RequestBody AIConfigRequest request) {
        adminService.switchProvider(request.provider());
        return ResponseEntity.ok(Map.of("provider", request.provider(), "status", "switched"));
    }

    /**
     * Sends a test push to the currently-authenticated admin's own active tokens.
     * The notification is created in the in-app feed too, so it's auditable.
     */
    @PostMapping("/notifications/test")
    public ResponseEntity<Map<String, String>> sendTestNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) TestNotificationRequest body) {
        NotificationType type = NotificationType.SYSTEM_ANNOUNCEMENT;
        String title = body != null && body.title() != null && !body.title().isBlank()
                ? body.title()
                : "Echo test push";
        String content = body != null && body.body() != null && !body.body().isBlank()
                ? body.body()
                : "If you see this on your lock screen, end-to-end push works.";
        notificationService.notifyTemplated(
                principal.getId(),
                type,
                templateService.systemAnnouncementVars(title, content),
                "SYSTEM",
                null,
                "admin_test:" + principal.getId() + ":" + System.currentTimeMillis(),
                null
        );
        return ResponseEntity.ok(Map.of("status", "queued"));
    }

    private record TestNotificationRequest(String title, String body) {}
}
