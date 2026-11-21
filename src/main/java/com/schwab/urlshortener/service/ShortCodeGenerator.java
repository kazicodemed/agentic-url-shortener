package com.schwab.urlshortener.service;

import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces candidate short codes. It does not check the database: uniqueness is
 * enforced by the unique constraint on {@code url_mappings.short_code}, and the
 * service retries on collision. A pre-check here would be check-then-act and
 * still racy under concurrency, so the constraint is the single source of truth.
 *
 * Extracted from {@link UrlShortenerService} so collisions can be forced in tests.
 */
@Component
public class ShortCodeGenerator {

    private final int length;

    public ShortCodeGenerator(@Value("${url-shortener.short-code-length}") int length) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("url-shortener.short-code-length must be 4..10 (column is 10 chars)");
        }
        this.length = length;
    }

    /**
     * @param seed usually the original URL; mixed with time and randomness so
     *             each call (including retries and concurrent calls) differs
     */
    public String generate(String seed) {
        String input = seed + ':' + System.nanoTime() + ':' + ThreadLocalRandom.current().nextLong();
        return Hashing.murmur3_128()
                .hashString(input, StandardCharsets.UTF_8)
                .toString()
                .substring(0, length);
    }
}
