package com.echo.controller;

import com.echo.dto.request.UpdateConsentRequest;
import com.echo.dto.response.ConsentStatusResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
public class PrivacyController {

    private final ConsentService consentService;

    /** Returns the user's current consent status. */
    @GetMapping("/consent")
    public ResponseEntity<ConsentStatusResponse> getConsent(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(consentService.getConsent(p.getId()));
    }

    /**
     * Update consent — KVKK Article 7: consent can always be withdrawn.
     * aiTrainingConsent: true/false — use of data in AI training (default: false)
     * kvkkExplicitConsent: true/false — KVKK explicit consent
     */
    @PutMapping("/consent")
    public ResponseEntity<ConsentStatusResponse> updateConsent(
            @RequestBody UpdateConsentRequest request,
            @AuthenticationPrincipal UserPrincipal p,
            HttpServletRequest httpRequest) {
        String ip        = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(consentService.updateConsent(p.getId(), request, ip, userAgent));
    }

    /**
     * Account deletion request — KVKK Article 11.
     * After 30 days a scheduled job deletes all personal data.
     */
    @PostMapping("/delete-account")
    public ResponseEntity<Void> requestAccountDeletion(@AuthenticationPrincipal UserPrincipal p) {
        consentService.requestAccountDeletion(p.getId());
        return ResponseEntity.accepted().build();
    }
}
