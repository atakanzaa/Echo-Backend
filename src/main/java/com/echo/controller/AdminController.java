package com.echo.controller;

import com.echo.dto.request.AIConfigRequest;
import com.echo.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

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
}
