package com.echo.ai;

import java.util.List;

/**
 * Structured response returned by the AI from a journal-entry analysis.
 * All providers (OpenAI, Gemini, Claude, Ollama) produce this format.
 */
public record AIAnalysisResponse(
        // core fields
        String       summary,
        double       moodScore,
        String       moodLabel,
        List<String> topics,
        String       reflectiveQuestion,
        List<String> keyEmotions,
        String       energyLevel,

        // structured inference fields
        List<AIGoal>                goals,
        List<String>                insights,
        List<AIDetectedAchievement> achievements,
        List<AICalendarEvent>       calendarEvents,
        boolean                     memoryWorthy,
        String                      memoryCapsuleTitle,

        // raw AI response — always last
        String rawJson
) {}
