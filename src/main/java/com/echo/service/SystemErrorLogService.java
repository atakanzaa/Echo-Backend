package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.system.SystemErrorLog;
import com.echo.repository.SystemErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemErrorLogService {

    private static final int MAX_MESSAGE = 500;
    private static final int MAX_CLASS = 160;
    private static final int MAX_PATH = 200;
    private static final int MAX_CODE = 64;
    private static final int MAX_METHOD = 8;

    private final SystemErrorLogRepository repository;
    private final AppProperties props;

    // Persists in its own transaction so the row survives the request rollback.
    // Never throws: logging an error must not mask or replace the original error.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String severity,
                       int httpStatus,
                       String errorCode,
                       String exceptionClass,
                       String message,
                       String path,
                       String method,
                       String requestId,
                       UUID userId) {
        try {
            SystemErrorLog entry = SystemErrorLog.builder()
                    .occurredAt(OffsetDateTime.now())
                    .severity(severity)
                    .httpStatus(httpStatus)
                    .errorCode(truncate(errorCode, MAX_CODE))
                    .exceptionClass(truncate(exceptionClass, MAX_CLASS))
                    .message(truncate(message, MAX_MESSAGE))
                    .path(truncate(path, MAX_PATH))
                    .method(truncate(method, MAX_METHOD))
                    .requestId(requestId)
                    .userId(userId)
                    .build();
            repository.save(entry);

        } catch (Exception ex) {
            log.debug("Failed to persist system error log entry: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void pruneOldEntries() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(props.getAdmin().getErrorLogRetentionDays());
        int deleted = repository.deleteByOccurredAtBefore(cutoff);

        if (deleted > 0) {

            log.info("Pruned {} system_error_log rows older than {} days",
                    deleted, props.getAdmin().getErrorLogRetentionDays());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {

            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
