package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.ShortenUrlResponse;
import com.schwab.urlshortener.dto.UrlAnalyticsResponse;
import com.schwab.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for creating short URLs and reading analytics, under /api/v1.
 * The public redirect (GET /{shortCode}) lives in RedirectController, outside
 * this prefix, so the short URLs handed out by the API actually resolve.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerController {

    private final UrlShortenerService urlShortenerService;

    /**
     * Create a shortened URL
     * 
     * POST /api/v1/shorten
     * Body: { "url": "https://example.com", "customCode": "optional", "expirationDays": 30 }
     */
    @PostMapping("/shorten")
    public ResponseEntity<ShortenUrlResponse> shortenUrl(@Valid @RequestBody ShortenUrlRequest request) {
        log.info("POST /api/v1/shorten - URL: {}", request.getUrl());
        ShortenUrlResponse response = urlShortenerService.shortenUrl(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get analytics for a short code
     * 
     * GET /api/v1/analytics/{shortCode}
     */
    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<UrlAnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        log.info("GET /api/v1/analytics/{}", shortCode);
        UrlAnalyticsResponse response = urlShortenerService.getAnalytics(shortCode);
        return ResponseEntity.ok(response);
    }
}
