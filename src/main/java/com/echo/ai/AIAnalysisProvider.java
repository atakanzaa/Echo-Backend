package com.echo.ai;

public interface AIAnalysisProvider {

    /**
     * Analyzes the transcript and returns a structured result.
     *
     * @param request transcript + user context
     * @return analysis result
     */
    AIAnalysisResponse analyze(AIAnalysisRequest request);

    GoalMatchDecision verifyGoalMatch(GoalMatchVerificationRequest request);
}
