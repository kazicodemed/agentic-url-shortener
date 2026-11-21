package com.schwab.urlshortener.exception;

/**
 * Exception thrown when URL validation fails
 */
public class InvalidUrlException extends RuntimeException {
    
    public InvalidUrlException(String message) {
        super(message);
    }
}
