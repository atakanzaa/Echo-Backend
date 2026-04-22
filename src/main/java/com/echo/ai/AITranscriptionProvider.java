package com.echo.ai;

public interface AITranscriptionProvider {

    /**
     * Ses dosyasını yazıya çevirir.
     *
     * @param request Ham ses verisi, metadata ve süre bilgisi
     * @return Transkript ve provider metadata
     */
    AITranscriptionResult transcribe(AITranscriptionRequest request);
}
