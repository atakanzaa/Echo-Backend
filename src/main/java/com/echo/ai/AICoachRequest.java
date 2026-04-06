package com.echo.ai;

import java.util.List;

public record AICoachRequest(
        String              userMessage,
        List<ChatMessage>   history,
        String              userSummary,
        String              moodContext,
        List<String>        recentTopics,
        List<String>        activeGoals,
        String              userName,
        String              language
) {
    public record ChatMessage(String role, String content) {}
}
