package com.echo.domain.system;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_error_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "exception_class", length = 160)
    private String exceptionClass;

    @Column(length = 500)
    private String message;

    @Column(length = 200)
    private String path;

    @Column(length = 8)
    private String method;

    @Column(nullable = false, length = 16)
    private String severity;
}
