package com.echo.exception;

public class AudioValidationException extends EchoException {
    private final String code;

    public AudioValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
