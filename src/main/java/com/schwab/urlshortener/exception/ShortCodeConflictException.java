package com.schwab.urlshortener.exception;

/**
 * The caller asked for a specific custom short code that is already taken.
 * Mapped to HTTP 409 Conflict. Never retried with a different code: the
 * caller chose this code on purpose.
 */
public class ShortCodeConflictException extends RuntimeException {

    public ShortCodeConflictException(String shortCode) {
        super("Custom code already exists: " + shortCode);
    }
}
