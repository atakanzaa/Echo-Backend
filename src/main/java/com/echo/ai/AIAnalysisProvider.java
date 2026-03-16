package com.echo.ai;

public interface AIAnalysisProvider {

    /**
     * Transkripti analiz eder, yapılandırılmış sonuç döner.
     *
     * @param request Transkript + kullanıcı bağlamı
     * @return Analiz sonucu
     */
    AIAnalysisResponse analyze(AIAnalysisRequest request);
}
