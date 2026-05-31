package com.echo.service.notification;

import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApnsPayloadBuilder {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_BODY_LENGTH = 240;

    public String build(ApnsPushPayload payload) {
        SimpleApnsPayloadBuilder builder = new SimpleApnsPayloadBuilder();
        builder.setAlertTitle(truncate(payload.title(), MAX_TITLE_LENGTH));
        builder.setAlertBody(truncate(payload.body(), MAX_BODY_LENGTH));
        builder.setSound("default");

        if (payload.threadId() != null && !payload.threadId().isBlank()) {
            builder.setThreadId(payload.threadId());
        }

        builder.addCustomProperty("type", payload.type().name());
        builder.addCustomProperty("notification_id", payload.notificationId().toString());

        Map<String, String> data = payload.data();
        if (data != null) {
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                builder.addCustomProperty(e.getKey(), e.getValue());
            }
        }

        return builder.build();
    }

    private String truncate(String input, int max) {
        if (input == null) return "";
        if (input.length() <= max) return input;
        return input.substring(0, max - 1) + "…";
    }
}
