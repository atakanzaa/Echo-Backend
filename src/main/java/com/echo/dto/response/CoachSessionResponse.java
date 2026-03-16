package com.echo.dto.response;
import com.echo.domain.coach.CoachSession;
import java.util.UUID;
public record CoachSessionResponse(UUID id, String title, String startedAt, String updatedAt, boolean isActive, UUID journalEntryId) {
    public static CoachSessionResponse from(CoachSession s) {
        return new CoachSessionResponse(
                s.getId(), s.getTitle(),
                s.getStartedAt().toString(), s.getUpdatedAt().toString(),
                s.isActive(),
                s.getJournalEntry() != null ? s.getJournalEntry().getId() : null
        );
    }
}
