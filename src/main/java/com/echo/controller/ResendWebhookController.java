package com.echo.controller;

import com.echo.service.ResendWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/resend")
@RequiredArgsConstructor
public class ResendWebhookController {

    private final ResendWebhookService resendWebhookService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload, HttpServletRequest request) {
        resendWebhookService.handleWebhook(payload, request);
        return ResponseEntity.ok().build();
    }
}
