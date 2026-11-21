package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.ClickInfo;
import com.schwab.urlshortener.entity.AnalyticsEvent;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Records click analytics off the redirect path.
 *
 * <p>A separate bean on purpose: {@code @Async} works through a Spring proxy, so
 * the method must be public and called from another bean. </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsRecorder {

    private final AnalyticsEventRepository analyticsEventRepository;

    /**
     * Fire-and-forget on the "analyticsExecutor" virtual-thread executor.
     * Failures are logged, never propagated: analytics must not break a redirect.
     */
    @Async("analyticsExecutor")
    public void record(ClickInfo click) {
        try {
            AnalyticsEvent event = AnalyticsEvent.builder()
                    .shortCode(click.shortCode())
                    .ipAddress(click.ipAddress())
                    .userAgent(click.userAgent())
                    .referer(click.referer())
                    .country("US")  // Simplified - would use GeoIP service in production
                    .build();

            analyticsEventRepository.save(event);
            log.debug("Tracked analytics event for: {}", click.shortCode());
        } catch (Exception e) {
            log.error("Failed to track analytics for {}", click.shortCode(), e);
        }
    }
}
