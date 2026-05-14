package com.echo.ai;

/**
 * AI synthesis provider interface — combines multiple user data sources.
 * Gemini and OpenAI implementations are available.
 */
public interface AISynthesisProvider {

    /**
     * Synthesizes the user's journal, coach, goal, and profile data.
     *
     * @param request consolidated user data
     * @return synthesis result (narrative, suggestions, growth, profile update)
     */
    AISynthesisResponse synthesize(AISynthesisRequest request);
}
