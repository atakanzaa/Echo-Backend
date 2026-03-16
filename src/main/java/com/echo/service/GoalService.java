package com.echo.service;

import com.echo.domain.goal.Goal;
import com.echo.dto.response.GoalResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;

    /** PENDING + ACTIVE hedefler (aktif hedef listesi) */
    @Transactional(readOnly = true)
    public List<GoalResponse> getActiveGoals(UUID userId) {
        return goalRepository
                .findByUserIdAndStatusOrderByDetectedAtDesc(userId, "PENDING")
                .stream()
                .map(GoalResponse::from)
                .toList();
    }

    /** Tüm hedefler (geçmiş dahil) */
    @Transactional(readOnly = true)
    public List<GoalResponse> getAllGoals(UUID userId) {
        return goalRepository
                .findByUserIdOrderByDetectedAtDesc(userId)
                .stream()
                .map(GoalResponse::from)
                .toList();
    }

    /** Hedefi tamamlandı olarak işaretle */
    @Transactional
    public GoalResponse completeGoal(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hedef bulunamadı"));

        goal.setStatus("COMPLETED");
        goal.setCompletedAt(OffsetDateTime.now());
        goalRepository.save(goal);

        log.info("Hedef tamamlandı: goalId={}, userId={}", goalId, userId);
        return GoalResponse.from(goal);
    }

    /** Hedefi reddedildi olarak işaretle */
    @Transactional
    public GoalResponse dismissGoal(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hedef bulunamadı"));

        goal.setStatus("DISMISSED");
        goalRepository.save(goal);

        log.info("Hedef reddedildi: goalId={}, userId={}", goalId, userId);
        return GoalResponse.from(goal);
    }
}
