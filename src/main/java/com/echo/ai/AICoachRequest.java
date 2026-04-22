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
        String              language,
        String              userNarrative
) {
    // Back-compat constructor — callers that haven't been updated yet.
    public AICoachRequest(
            String              userMessage,
            List<ChatMessage>   history,
            String              userSummary,
            String              moodContext,
            List<String>        recentTopics,
            List<String>        activeGoals,
            String              userName,
            String              language
    ) {
        this(userMessage, history, userSummary, moodContext, recentTopics, activeGoals,
                userName, language, null);
    }

    public record ChatMessage(String role, String content) {}
}
