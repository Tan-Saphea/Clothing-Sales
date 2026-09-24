package com.clothing.app.exception;

public class DatabaseBusinessException extends RuntimeException {
    public DatabaseBusinessException(String message) {
        super(message);
    }

    public DatabaseBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
