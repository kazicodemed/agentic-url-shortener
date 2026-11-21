package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.ClickInfo;
import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.ShortenUrlResponse;
import com.schwab.urlshortener.dto.UrlAnalyticsResponse;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.exception.InvalidUrlException;
import com.schwab.urlshortener.exception.ShortCodeConflictException;
import com.schwab.urlshortener.exception.ShortCodeGenerationException;
import com.schwab.urlshortener.exception.UrlNotFoundException;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for URL shortening operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    private final UrlMappingRepository urlMappingRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final AnalyticsRecorder analyticsRecorder;

    @Value("${url-shortener.base-url}")
    private String baseUrl;

    static final int MAX_SAVE_ATTEMPTS = 3;

    /**
     * Create a shortened URL with proper concurrency handling.
     *
     * <p>Uses the database unique constraint on short_code plus a bounded retry.
     *
     * <p>Deliberately NOT {@code @Transactional}: once an insert hits a constraint
     * violation inside a transaction, Spring marks that transaction rollback-only,
     * so any retry inside the same transaction is doomed (it ends in
     * {@code UnexpectedRollbackException}). Instead each save attempt runs in its
     * own short transaction (the repository's {@code saveAndFlush}), so a failed
     * attempt rolls back on its own and the next attempt starts clean.
     */
    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request) {
        log.info("Creating short URL for: {}", request.getUrl());

        // Validate URL
        if (!isValidUrl(request.getUrl())) {
            throw new InvalidUrlException("Invalid URL format: " + request.getUrl());
        }

        // Check if URL already exists (optimization to avoid regeneration)
        Optional<UrlMapping> existing = urlMappingRepository.findByOriginalUrl(request.getUrl());
        if (existing.isPresent() && existing.get().getIsActive()) {
            log.info("URL already exists, returning existing short code");
            return buildResponse(existing.get());
        }

        boolean customCode = request.getCustomCode() != null;
        String shortCode = customCode
            ? validateCustomCode(request.getCustomCode())
            : shortCodeGenerator.generate(request.getUrl());

        // Calculate expiration
        LocalDateTime expiresAt = request.getExpirationDays() != null
            ? LocalDateTime.now().plusDays(request.getExpirationDays())
            : null;

        return saveWithRetry(shortCode, customCode, request.getUrl(), expiresAt);
    }

    /**
     * Save the mapping, retrying with a fresh generated code if another request
     * took the same code first.
     *
     * <ul>
     *   <li>Each attempt is its own transaction ({@code saveAndFlush} on the repository).</li>
     *   <li>A custom code is never swapped for a random one: a collision means 409.</li>
     *   <li>Only short-code collisions are retried. Any other integrity error
     *       (e.g. a value too long for its column) is rethrown at once.</li>
     *   <li>Bounded: after {@link #MAX_SAVE_ATTEMPTS} collisions we give up with a
     *       retryable 503 instead of looping.</li>
     * </ul>
     */
    private ShortenUrlResponse saveWithRetry(String shortCode, boolean customCode,
                                             String originalUrl, LocalDateTime expiresAt) {
        String currentShortCode = shortCode;

        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            UrlMapping mapping = UrlMapping.builder()
                .shortCode(currentShortCode)
                .originalUrl(originalUrl)
                .expiresAt(expiresAt)
                .clickCount(0L)
                .isActive(true)
                .build();
            try {
                UrlMapping saved = urlMappingRepository.saveAndFlush(mapping);
                log.info("Created short code {} (attempt {}/{})", currentShortCode, attempt, MAX_SAVE_ATTEMPTS);
                return buildResponse(saved);

            } catch (DataIntegrityViolationException e) {
                if (!urlMappingRepository.existsByShortCode(currentShortCode)) {
                    // The violation was not a short-code collision; retrying would not help.
                    throw e;
                }
                if (customCode) {
                    // Someone else claimed this custom code between our check and our insert.
                    throw new ShortCodeConflictException(currentShortCode);
                }
                log.warn("Short code collision: {} (attempt {}/{})", currentShortCode, attempt, MAX_SAVE_ATTEMPTS);
                if (attempt >= MAX_SAVE_ATTEMPTS) {
                    throw new ShortCodeGenerationException(MAX_SAVE_ATTEMPTS, e);
                }
                currentShortCode = shortCodeGenerator.generate(originalUrl);
            }
        }
        
        // Should never reach here due to throw in last iteration, but satisfies compiler
        throw new IllegalStateException("Exhausted " + MAX_SAVE_ATTEMPTS + " save attempts");
    }

    /**
     * Resolve short code to original URL and track analytics
     */
    @Transactional
    public String resolveUrl(String shortCode, HttpServletRequest request) {
        log.info("Resolving short code: {}", shortCode);

        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Check if URL is active
        if (!mapping.getIsActive()) {
            throw new UrlNotFoundException(shortCode);
        }

        // Check expiration
        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Short code {} has expired", shortCode);
            throw new UrlNotFoundException(shortCode);
        }

        // Increment click count
        urlMappingRepository.incrementClickCount(shortCode);

        // Copy what analytics needs now, on the request thread, then hand off.
        // AnalyticsRecorder is a separate bean, so @Async really runs on analyticsExecutor.
        analyticsRecorder.record(ClickInfo.from(shortCode, request));

        return mapping.getOriginalUrl();
    }

    /**
     * Get analytics for a short code
     */
    @Cacheable(value = "analytics", key = "#shortCode")
    public UrlAnalyticsResponse getAnalytics(String shortCode) {
        log.info("Fetching analytics for: {}", shortCode);

        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new UrlNotFoundException(shortCode));

        LocalDateTime now = LocalDateTime.now();
        Long clicksLast24Hours = analyticsEventRepository.countByShortCodeAndTimestampBetween(
            shortCode, now.minusHours(24), now
        );
        Long clicksLast7Days = analyticsEventRepository.countByShortCodeAndTimestampBetween(
            shortCode, now.minusDays(7), now
        );

        // Get clicks by country
        Map<String, Long> clicksByCountry = new HashMap<>();
        List<Object[]> countryData = analyticsEventRepository.getTopCountriesByShortCode(shortCode);
        for (Object[] row : countryData) {
            clicksByCountry.put((String) row[0], (Long) row[1]);
        }

        return UrlAnalyticsResponse.builder()
            .shortCode(shortCode)
            .originalUrl(mapping.getOriginalUrl())
            .totalClicks(mapping.getClickCount())
            .createdAt(mapping.getCreatedAt())
            .clicksByCountry(clicksByCountry)
            .clicksLast24Hours(clicksLast24Hours)
            .clicksLast7Days(clicksLast7Days)
            .build();
    }

    /**
     * Validate a caller-supplied short code. The exists check gives a fast,
     * friendly 409 in the common case; the unique constraint in saveWithRetry
     * still catches the race where two requests claim the same code at once.
     */
    private String validateCustomCode(String customCode) {
        if (customCode.length() < 3 || customCode.length() > 20) {
            throw new InvalidUrlException("Custom code must be between 3 and 20 characters");
        }

        if (!customCode.matches("^[a-zA-Z0-9_-]+$")) {
            throw new InvalidUrlException("Custom code can only contain letters, numbers, hyphens, and underscores");
        }

        if (urlMappingRepository.existsByShortCode(customCode)) {
            throw new ShortCodeConflictException(customCode);
        }

        return customCode;
    }

    /**
     * Validate URL format
     */
    private boolean isValidUrl(String url) {
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        return urlValidator.isValid(url);
    }

    /**
     * Build response DTO
     */
    private ShortenUrlResponse buildResponse(UrlMapping mapping) {
        return ShortenUrlResponse.builder()
            .shortCode(mapping.getShortCode())
            .shortUrl(baseUrl + "/" + mapping.getShortCode())
            .originalUrl(mapping.getOriginalUrl())
            .createdAt(mapping.getCreatedAt())
            .expiresAt(mapping.getExpiresAt())
            .build();
    }
}
