package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public short-URL redirect: GET /{shortCode} -> 302 to the original URL.
 *
 * <p>Kept separate from UrlShortenerController because that controller's
 * class-level @RequestMapping("/api/v1") would put the redirect at
 * /api/v1/{shortCode}, while the API hands out short URLs as baseUrl/{shortCode}.
 *
 * <p>The path pattern only matches plausible short codes (letters, digits,
 * '-' and '_', 3 to 20 characters, the same rules as custom codes). Paths with
 * a dot or a slash, such as /favicon.ico, /actuator/health or /h2-console,
 * never reach this method.
 *
 * <p>302 (not 301) on purpose: browsers cache 301s, so repeat visits would skip
 * this service and click analytics would be lost.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    @GetMapping("/{shortCode:[A-Za-z0-9_-]{3,20}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        log.info("GET /{} - redirecting", shortCode);
        String originalUrl = urlShortenerService.resolveUrl(shortCode, request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(originalUrl).toString())
                .build();
    }
}
