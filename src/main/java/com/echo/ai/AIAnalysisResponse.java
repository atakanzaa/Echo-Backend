package com.echo.ai;

import java.util.List;

/**
 * AI'nın günlük giriş analizinden döndürdüğü yapılandırılmış yanıt.
 * Tüm provider'lar (OpenAI, Gemini, Claude, Ollama) bu formatı üretir.
 */
public record AIAnalysisResponse(
        // Mevcut alanlar
        String       summary,
        double       moodScore,
        String       moodLabel,
        List<String> topics,
        String       reflectiveQuestion,
        List<String> keyEmotions,
        String       energyLevel,

        // Yeni: yapılandırılmış çıkarım alanları
        List<AIGoal>                goals,
        List<String>                insights,
        List<AIDetectedAchievement> achievements,
        List<AICalendarEvent>       calendarEvents,
        boolean                     memoryWorthy,
        String                      memoryCapsuleTitle,

        // Ham AI yanıtı — her zaman sonda
        String rawJson
) {}
