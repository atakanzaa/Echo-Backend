package com.echo.ai;

/**
 * AI'nın günlük girişinden tespit ettiği başarı/kazanım.
 * Örnek: "Proje tamamlandı" → title="Completed project milestone", type="work"
 */
public record AIDetectedAchievement(
        String title,
        String type    // "work" | "health" | "personal" | "relationship"
) {}
