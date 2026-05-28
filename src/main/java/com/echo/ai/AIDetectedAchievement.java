package com.echo.ai;

/**
 * An achievement detected by the AI from a journal entry.
 * Example: "Finished the project" → title="Completed project milestone", type="work"
 */
public record AIDetectedAchievement(
        String title,
        String type    // "work" | "health" | "personal" | "relationship"
) {}
