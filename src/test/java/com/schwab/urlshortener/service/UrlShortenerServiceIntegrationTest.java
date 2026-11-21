package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.ShortenUrlResponse;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.exception.ShortCodeGenerationException;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression test for the "retry inside one transaction" bug, against real H2.
 *
 * The generator is mocked so the first code collides with a row that already
 * exists, which triggers a real unique-constraint violation. With the old code
 * (retry loop inside a single @Transactional method) the retry could not
 * recover: the transaction was already rollback-only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UrlShortenerServiceIntegrationTest {

    @Autowired UrlShortenerService service;
    @Autowired UrlMappingRepository urlMappingRepository;
    @Autowired AnalyticsEventRepository analyticsEventRepository;
    @MockitoBean ShortCodeGenerator shortCodeGenerator;

    @BeforeEach
    void cleanDatabase() {
        analyticsEventRepository.deleteAll();
        urlMappingRepository.deleteAll();
        urlMappingRepository.saveAndFlush(UrlMapping.builder()
                .shortCode("dup0001")
                .originalUrl("https://already-taken.example.com")
                .clickCount(0L)
                .isActive(true)
                .build());
    }

    @Test
    void recoversFromRealConstraintViolationWithANewCode() {
        when(shortCodeGenerator.generate(anyString())).thenReturn("dup0001", "new0001");

        ShortenUrlResponse response = service.shortenUrl(
                ShortenUrlRequest.builder().url("https://example.com/a").build());

        assertThat(response.getShortCode()).isEqualTo("new0001");
        assertThat(urlMappingRepository.findByShortCode("new0001"))
                .get()
                .extracting(UrlMapping::getOriginalUrl)
                .isEqualTo("https://example.com/a");
        assertThat(urlMappingRepository.count()).isEqualTo(2);
    }

    @Test
    void persistentCollisionsEndInRetryableErrorAndLeaveNoPartialRows() {
        when(shortCodeGenerator.generate(anyString())).thenReturn("dup0001");

        assertThatThrownBy(() -> service.shortenUrl(
                ShortenUrlRequest.builder().url("https://example.com/b").build()))
                .isInstanceOf(ShortCodeGenerationException.class);

        assertThat(urlMappingRepository.count()).isEqualTo(1);
    }
}
