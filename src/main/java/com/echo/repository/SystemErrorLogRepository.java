package com.echo.repository;

import com.echo.domain.system.SystemErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemErrorLogRepository extends JpaRepository<SystemErrorLog, UUID> {

    Page<SystemErrorLog> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long countByOccurredAtAfter(OffsetDateTime since);

    @Modifying
    @Query("DELETE FROM SystemErrorLog e WHERE e.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
