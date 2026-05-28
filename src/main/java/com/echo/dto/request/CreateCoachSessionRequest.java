package com.echo.dto.request;

import java.util.UUID;

/** Create a coach session — journalEntryId is optional, for talking about a journal entry. */
public record CreateCoachSessionRequest(UUID journalEntryId) {}
