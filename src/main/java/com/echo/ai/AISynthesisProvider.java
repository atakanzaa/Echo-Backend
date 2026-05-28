package com.echo.ai;

/**
 * AI Synthesis provider interface — synthesizes multiple data sources.
 * Gemini and OpenAI implementations are available.
 */
public interface AISynthesisProvider {

    /**
     * Synthesizes the user's journal, coach, goal and profile data.
     *
     * @param request combined user data
     * @return synthesis result (narrative, suggestions, growth, profile update)
     */
    AISynthesisResponse synthesize(AISynthesisRequest request);
}
