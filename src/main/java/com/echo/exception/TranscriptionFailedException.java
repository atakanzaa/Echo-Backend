package com.echo.exception;

import lombok.Getter;

@Getter
public class TranscriptionFailedException extends EchoException {

    private final String code;
    private final String userMessage;

    public TranscriptionFailedException(String code, String userMessage) {
        super(userMessage);
        this.code = code;
        this.userMessage = userMessage;
    }

    public TranscriptionFailedException(String code, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.userMessage = userMessage;
    }
}
