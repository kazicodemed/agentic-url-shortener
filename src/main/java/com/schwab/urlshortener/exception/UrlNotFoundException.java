package com.schwab.urlshortener.exception;

/**
 * Exception thrown when a short code is not found
 */
public class UrlNotFoundException extends RuntimeException {
    
    public UrlNotFoundException(String shortCode) {
        super("URL not found for short code: " + shortCode);
    }
}
