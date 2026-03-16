package com.echo.controller;

import com.echo.dto.response.CalendarMonthResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping
    public ResponseEntity<CalendarMonthResponse> getMonth(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(calendarService.getMonth(principal.getId(), year, month));
    }
}
