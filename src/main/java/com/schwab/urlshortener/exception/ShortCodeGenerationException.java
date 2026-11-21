package com.schwab.urlshortener.exception;

/**
 * Could not find a free generated short code within the bounded number of
 * attempts. Mapped to HTTP 503: the condition is transient and the client may retry.
 */
public class ShortCodeGenerationException extends RuntimeException {

    public ShortCodeGenerationException(int attempts, Throwable cause) {
        super("Failed to generate a unique short code after " + attempts + " attempts", cause);
    }
}
