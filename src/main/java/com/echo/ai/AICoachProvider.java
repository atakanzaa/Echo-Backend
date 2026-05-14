package com.echo.ai;

public interface AICoachProvider {

    /**
     * Continues the reflection coach conversation.
     *
     * @param request user message plus prior conversation history
     * @return AI coach response
     */
    AICoachResponse chat(AICoachRequest request);
}
