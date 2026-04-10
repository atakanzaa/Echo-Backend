package com.echo.repository;

import com.echo.domain.goal.Goal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdAndStatusOrderByDetectedAtDesc(UUID userId, String status);

    List<Goal> findByUserIdAndStatusInOrderByDetectedAtDesc(UUID userId, List<String> statuses);

    Page<Goal> findByUserIdAndStatusInOrderByDetectedAtDesc(UUID userId, List<String> statuses, Pageable pageable);

    List<Goal> findByUserIdAndStatusNotOrderByDetectedAtDesc(UUID userId, String status);

    Page<Goal> findByUserIdAndStatusNotOrderByDetectedAtDesc(UUID userId, String status, Pageable pageable);

    List<Goal> findByUserIdOrderByDetectedAtDesc(UUID userId);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    Optional<Goal> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, String status);

    int countByUserIdAndStatus(UUID userId, String status);

    int countByUserIdAndStatusIn(UUID userId, List<String> statuses);

    boolean existsByUserIdAndSourceJournalEntryIdAndTitle(UUID userId,
                                                           UUID sourceJournalEntryId,
                                                           String title);
}
