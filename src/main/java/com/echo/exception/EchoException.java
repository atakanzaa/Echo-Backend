package com.echo.exception;

public class EchoException extends RuntimeException {
    public EchoException(String message) {
        super(message);
    }
    public EchoException(String message, Throwable cause) {
        super(message, cause);
    }
}
