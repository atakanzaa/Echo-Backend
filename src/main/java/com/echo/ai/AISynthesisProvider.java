package com.echo.ai;

/**
 * AI Synthesis provider interface — birden fazla veri kaynağını sentezler.
 * Gemini ve OpenAI implementasyonları mevcuttur.
 */
public interface AISynthesisProvider {

    /**
     * Kullanıcının journal, coach, goal ve profil verilerini sentezler.
     *
     * @param request Birleştirilmiş kullanıcı verileri
     * @return Sentez sonucu (narrative, suggestions, growth, profile update)
     */
    AISynthesisResponse synthesize(AISynthesisRequest request);
}
