package com.echo.controller;

import com.echo.domain.journal.SummaryPeriod;
import com.echo.dto.response.SummaryResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService summaryService;

    @GetMapping
    public ResponseEntity<SummaryResponse> getSummary(
            @RequestParam int period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal UserPrincipal principal) {

        SummaryPeriod summaryPeriod = SummaryPeriod.fromDays(period);
        LocalDate resolvedEnd = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(summaryService.getSummary(principal.getId(), summaryPeriod, resolvedEnd));
    }
}
