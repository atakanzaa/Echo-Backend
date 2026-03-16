package com.echo.dto.response;
import com.echo.domain.coach.CoachMessage;
import java.util.UUID;
public record CoachMessageResponse(UUID id, String role, String content, String createdAt) {
    public static CoachMessageResponse from(CoachMessage m) {
        return new CoachMessageResponse(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getCreatedAt().toString());
    }
}
