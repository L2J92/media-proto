package com.live.media;

public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
