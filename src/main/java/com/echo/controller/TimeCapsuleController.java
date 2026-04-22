package com.echo.controller;

import com.echo.dto.request.CreateCapsuleRequest;
import com.echo.dto.response.PagedResponse;
import com.echo.dto.response.TimeCapsuleResponse;
import com.echo.security.UserPrincipal;
import com.echo.service.TimeCapsuleService;
import com.echo.util.PageableFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/capsules")
@RequiredArgsConstructor
public class TimeCapsuleController {
    private final TimeCapsuleService timeCapsuleService;
    private final PageableFactory pageableFactory;

    @GetMapping
    public ResponseEntity<PagedResponse<TimeCapsuleResponse>> getCapsules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal p) {
        Pageable pageable = pageableFactory.create(page, size);
        return ResponseEntity.ok(timeCapsuleService.getCapsules(p.getId(), pageable));
    }

    @PostMapping
    public ResponseEntity<TimeCapsuleResponse> createCapsule(
            @Valid @RequestBody CreateCapsuleRequest request,
            @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(timeCapsuleService.createCapsule(p.getId(), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TimeCapsuleResponse> getCapsule(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.ok(timeCapsuleService.getCapsule(id, p.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCapsule(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
        timeCapsuleService.deleteCapsule(id, p.getId());
        return ResponseEntity.noContent().build();
    }
}
