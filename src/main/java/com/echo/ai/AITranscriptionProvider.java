package com.echo.ai;

public interface AITranscriptionProvider {

    /**
     * Transcribes the audio file to text.
     *
     * @param request raw audio bytes, metadata and duration
     * @return transcript and provider metadata
     */
    AITranscriptionResult transcribe(AITranscriptionRequest request);
}
