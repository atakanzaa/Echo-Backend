package com.echo.repository;

import com.echo.domain.goal.Goal;
import com.echo.domain.goal.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdAndStatusOrderByDetectedAtDesc(UUID userId, GoalStatus status);

    List<Goal> findByUserIdAndStatusInOrderByDetectedAtDesc(UUID userId, List<GoalStatus> statuses);

    Page<Goal> findByUserIdAndStatusInOrderByDetectedAtDesc(UUID userId, List<GoalStatus> statuses, Pageable pageable);

    List<Goal> findByUserIdAndStatusNotOrderByDetectedAtDesc(UUID userId, GoalStatus status);

    Page<Goal> findByUserIdAndStatusNotOrderByDetectedAtDesc(UUID userId, GoalStatus status, Pageable pageable);

    List<Goal> findByUserIdOrderByDetectedAtDesc(UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    Optional<Goal> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, GoalStatus status);

    int countByUserIdAndStatus(UUID userId, GoalStatus status);

    int countByUserIdAndStatusIn(UUID userId, List<GoalStatus> statuses);

    boolean existsByUserIdAndSourceJournalEntryIdAndTitle(UUID userId,
                                                           UUID sourceJournalEntryId,
                                                           String title);
}
