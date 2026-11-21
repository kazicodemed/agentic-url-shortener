package com.schwab.urlshortener.service;

import com.schwab.urlshortener.entity.AnalyticsEvent;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves analytics really runs asynchronously on the analyticsExecutor.
 *
 * With the old code (private trackAnalytics called through "this") the save ran
 * on the caller's thread, so the captured thread name would be "main" here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnalyticsAsyncIntegrationTest {

    @Autowired UrlShortenerService service;
    @Autowired UrlMappingRepository urlMappingRepository;
    @MockitoBean AnalyticsEventRepository analyticsEventRepository;

    @Test
    void redirectRecordsAnalyticsOnAnalyticsThreadWithCopiedRequestData() throws Exception {
        urlMappingRepository.saveAndFlush(UrlMapping.builder()
                .shortCode("asy0001")
                .originalUrl("https://example.com/target")
                .clickCount(0L)
                .isActive(true)
                .build());

        CountDownLatch saved = new CountDownLatch(1);
        AtomicReference<String> savingThread = new AtomicReference<>();
        AtomicReference<AnalyticsEvent> savedEvent = new AtomicReference<>();
        when(analyticsEventRepository.save(any())).thenAnswer(inv -> {
            savingThread.set(Thread.currentThread().getName());
            savedEvent.set(inv.getArgument(0));
            saved.countDown();
            return inv.getArgument(0);
        });

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        String target = service.resolveUrl("asy0001", request);

        assertThat(target).isEqualTo("https://example.com/target");
        assertThat(saved.await(5, TimeUnit.SECONDS)).as("analytics event saved").isTrue();
        assertThat(savingThread.get()).startsWith("analytics-");
        assertThat(savingThread.get()).isNotEqualTo(Thread.currentThread().getName());
        assertThat(savedEvent.get().getShortCode()).isEqualTo("asy0001");
        assertThat(savedEvent.get().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(savedEvent.get().getUserAgent()).isEqualTo("JUnit");
    }
}
