package com.echo.dto.response;

import com.echo.domain.capsule.TimeCapsule;

import java.util.UUID;

public record TimeCapsuleResponse(
        UUID   id,
        String title,
        String contentType,
        String contentText,
        Integer audioDuration,
        String status,
        String sealedAt,
        String unlockAt,
        String openedAt,
        boolean canOpen
) {
    public static TimeCapsuleResponse from(TimeCapsule capsule) {
        boolean opened = TimeCapsule.STATUS_OPENED.equals(capsule.getStatus());
        return new TimeCapsuleResponse(
                capsule.getId(),
                capsule.getTitle(),
                capsule.getContentType(),
                opened ? capsule.getContentText() : null,
                capsule.getAudioDuration(),
                capsule.getStatus(),
                capsule.getSealedAt().toString(),
                capsule.getUnlockAt().toString(),
                capsule.getOpenedAt() != null ? capsule.getOpenedAt().toString() : null,
                capsule.isUnlocked()
        );
    }
}
