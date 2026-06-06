package com.echo.controller;

import com.echo.dto.response.AdminErrorResponse;
import com.echo.dto.response.AdminSummaryResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.service.AdminMonitorService;
import com.echo.util.PageableFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/monitor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;
    private final PageableFactory pageableFactory;

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminMonitorService.getSummary());
    }

    @GetMapping("/errors")
    public ResponseEntity<PagedResponse<AdminErrorResponse>> getRecentErrors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                adminMonitorService.getRecentErrors(pageableFactory.create(page, size)));
    }
}
