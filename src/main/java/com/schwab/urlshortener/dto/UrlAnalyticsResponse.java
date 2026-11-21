package com.schwab.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for URL analytics.
 * Serializable because it is stored in the "analytics" cache, and the Redis
 * cache uses Java serialization by default.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlAnalyticsResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String shortCode;
    private String originalUrl;
    private Long totalClicks;
    private LocalDateTime createdAt;
    private Map<String, Long> clicksByCountry;  // Country -> count
    private Long clicksLast24Hours;
    private Long clicksLast7Days;
}
