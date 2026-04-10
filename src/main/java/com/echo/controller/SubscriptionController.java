package com.echo.controller;

import com.echo.dto.request.RestorePurchaseRequest;
import com.echo.dto.request.VerifyPurchaseRequest;
import com.echo.dto.response.QuotaStatusResponse;
import com.echo.dto.response.SubscriptionResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.EntitlementService;
import com.echo.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final EntitlementService entitlementService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/status")
    public QuotaStatusResponse getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return entitlementService.getQuotaStatus(principal.getId());
    }

    @PostMapping("/verify")
    public SubscriptionResponse verifyPurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VerifyPurchaseRequest request) {
        return subscriptionService.verifyAndActivate(principal.getId(), request.signedTransaction());
    }

    @PostMapping("/restore")
    public SubscriptionResponse restorePurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody RestorePurchaseRequest request) {
        return subscriptionService.restore(principal.getId(), request.signedTransaction());
    }

    @PostMapping("/apple/notify")
    public ResponseEntity<Void> handleAppleNotification(@RequestBody String signedPayload) {
        subscriptionService.handleAppleNotification(signedPayload);
        return ResponseEntity.ok().build();
    }
}
