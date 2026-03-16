package com.echo.ai;

public interface AICoachProvider {

    /**
     * Reflection coach sohbetini sürdürür.
     *
     * @param request Kullanıcı mesajı + önceki konuşma geçmişi
     * @return AI coach yanıtı
     */
    AICoachResponse chat(AICoachRequest request);
}
