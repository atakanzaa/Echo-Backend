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

    /** Kullanıcının mevcut onay durumunu döner */
    @GetMapping("/consent")
    public ResponseEntity<ConsentStatusResponse> getConsent(@AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(consentService.getConsent(p.getId()));
    }

    /**
     * Onay güncelleme — KVKK Madde 7: rıza her zaman geri alınabilir.
     * aiTrainingConsent: true/false — AI eğitiminde veri kullanımı (varsayılan: false)
     * kvkkExplicitConsent: true/false — KVKK açık rıza
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
     * Hesap silme talebi — KVKK Madde 11.
     * 30 gün sonra scheduled job tüm kişisel verileri siler.
     */
    @PostMapping("/delete-account")
    public ResponseEntity<Void> requestAccountDeletion(@AuthenticationPrincipal UserPrincipal p) {
        consentService.requestAccountDeletion(p.getId());
        return ResponseEntity.accepted().build();
    }
}
