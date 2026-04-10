package com.echo.repository;

import com.echo.domain.capsule.TimeCapsule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, UUID> {
    List<TimeCapsule> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<TimeCapsule> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    Optional<TimeCapsule> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndSourceJournalEntryId(UUID userId, UUID sourceJournalEntryId);
    List<TimeCapsule> findByStatusAndUnlockAtLessThanEqual(String status, OffsetDateTime unlockAt);
    int countByUserIdAndStatus(UUID userId, String status);
}
