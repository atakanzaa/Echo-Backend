package com.echo.dto.request;

import java.util.UUID;

/** Coach oturumu oluşturma — journalEntryId opsiyonel, journal hakkında konuşmak için */
public record CreateCoachSessionRequest(UUID journalEntryId) {}
