package com.echo.controller;

import com.echo.dto.response.AIInsightsResponse;
import com.echo.dto.response.InsightsPeriodEligibilityResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.AIInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-insights")
@RequiredArgsConstructor
public class AIInsightsController {

    private final AIInsightsService aiInsightsService;

    @GetMapping("/eligibility")
    public ResponseEntity<InsightsPeriodEligibilityResponse> getEligibility(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(aiInsightsService.getEligibility(principal.getId()));
    }

    @GetMapping
    public ResponseEntity<AIInsightsResponse> getInsights(
            @RequestParam(defaultValue = "7") int period,
            @AuthenticationPrincipal UserPrincipal principal) {

        int validPeriod = List.of(7, 30, 90, 180, 365).contains(period) ? period : 7;
        return ResponseEntity.ok(aiInsightsService.getInsights(principal.getId(), validPeriod));
    }
}
