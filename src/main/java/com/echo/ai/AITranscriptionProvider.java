package com.echo.ai;

public interface AITranscriptionProvider {

    /**
     * Ses dosyasını yazıya çevirir.
     *
     * @param audioBytes Ham ses verisi (M4A/WAV)
     * @param filename   Orijinal dosya adı (uzantı ile birlikte)
     * @return Transkript metni
     */
    String transcribe(byte[] audioBytes, String filename);
}
