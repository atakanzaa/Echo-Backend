package com.echo.repository;

import com.echo.domain.goal.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /** Belirli statüdeki hedefleri getir (PENDING, COMPLETED, vb.) */
    List<Goal> findByUserIdAndStatusOrderByDetectedAtDesc(UUID userId, String status);

    /** Tüm hedefleri getir — geçmiş dahil */
    List<Goal> findByUserIdOrderByDetectedAtDesc(UUID userId);

    /** Kullanıcıya ait tekil hedef — yetki kontrolü için */
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    /** Belirli statüdeki hedef sayısı — synthesis için tamamlanan hedef sayısında kullanılır */
    int countByUserIdAndStatus(UUID userId, String status);

    boolean existsByUserIdAndSourceJournalEntryIdAndTitle(UUID userId,
                                                           UUID sourceJournalEntryId,
                                                           String title);
}
