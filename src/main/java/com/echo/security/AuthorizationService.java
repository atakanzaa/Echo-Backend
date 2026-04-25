package com.echo.security;

import com.echo.repository.CoachSessionRepository;
import com.echo.repository.GoalRepository;
import com.echo.repository.GoalSuggestionRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.TimeCapsuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Centralised resource-ownership checks used by {@code @PreAuthorize("@authz.canAccess...")}.
 *
 * Bean name {@code "authz"} keeps the SpEL short. Each method returns a boolean so Spring
 * Security maps a {@code false} return to 403 (AccessDeniedException). All checks pull the
 * caller from the SecurityContext so controllers don't have to forward the principal.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationService {

    private final JournalEntryRepository journalEntryRepository;
    private final CoachSessionRepository coachSessionRepository;
    private final GoalRepository goalRepository;
    private final GoalSuggestionRepository goalSuggestionRepository;
    private final TimeCapsuleRepository timeCapsuleRepository;

    @Transactional(readOnly = true)
    public boolean canAccessJournalEntry(UUID id) {
        UUID userId = currentUserId();
        return userId != null && journalEntryRepository.findByIdAndUserId(id, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAccessCoachSession(UUID id) {
        UUID userId = currentUserId();
        return userId != null && coachSessionRepository.findByIdAndUserId(id, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAccessGoal(UUID id) {
        UUID userId = currentUserId();
        return userId != null && goalRepository.findByIdAndUserId(id, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAccessGoalSuggestion(UUID id) {
        UUID userId = currentUserId();
        return userId != null && goalSuggestionRepository.findByIdAndUserId(id, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAccessTimeCapsule(UUID id) {
        UUID userId = currentUserId();
        return userId != null && timeCapsuleRepository.findByIdAndUserId(id, userId).isPresent();
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        if (auth.getPrincipal() instanceof UserPrincipal p) return p.getId();
        return null;
    }
}
