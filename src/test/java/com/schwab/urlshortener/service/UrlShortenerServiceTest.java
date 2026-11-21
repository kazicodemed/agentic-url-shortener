package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.ShortenUrlRequest;
import com.schwab.urlshortener.dto.ShortenUrlResponse;
import com.schwab.urlshortener.entity.UrlMapping;
import com.schwab.urlshortener.exception.ShortCodeConflictException;
import com.schwab.urlshortener.exception.ShortCodeGenerationException;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import com.schwab.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retry rules for short-code creation, with the database mocked.
 * See UrlShortenerServiceIntegrationTest for the same flow against a real database.
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    private static final String URL = "https://example.com/some/page";

    @Mock UrlMappingRepository urlMappingRepository;
    @Mock AnalyticsEventRepository analyticsEventRepository;
    @Mock ShortCodeGenerator shortCodeGenerator;
    @Mock AnalyticsRecorder analyticsRecorder;
    @InjectMocks UrlShortenerService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
    }

    @Test
    void retriesWithFreshCodeWhenGeneratedCodeCollides() {
        when(shortCodeGenerator.generate(URL)).thenReturn("aaaaaaa", "bbbbbbb");
        when(urlMappingRepository.saveAndFlush(any())).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            if (m.getShortCode().equals("aaaaaaa")) {
                throw new DataIntegrityViolationException("duplicate short_code");
            }
            return m;
        });
        when(urlMappingRepository.existsByShortCode("aaaaaaa")).thenReturn(true);

        ShortenUrlResponse response = service.shortenUrl(request(URL, null));

        assertThat(response.getShortCode()).isEqualTo("bbbbbbb");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/bbbbbbb");
        verify(urlMappingRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void customCodeAlreadyTakenIsConflictAndNothingIsSaved() {
        when(urlMappingRepository.existsByShortCode("promo1")).thenReturn(true);

        assertThatThrownBy(() -> service.shortenUrl(request(URL, "promo1")))
                .isInstanceOf(ShortCodeConflictException.class);

        verify(urlMappingRepository, never()).saveAndFlush(any());
    }

    @Test
    void customCodeLostRaceIsConflictAndNeverSwappedForRandomCode() {
        // the check passes, but another request inserts "promo1" before we do
        when(urlMappingRepository.existsByShortCode("promo1")).thenReturn(false, true);
        when(urlMappingRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate short_code"));

        assertThatThrownBy(() -> service.shortenUrl(request(URL, "promo1")))
                .isInstanceOf(ShortCodeConflictException.class)
                .hasMessageContaining("promo1");

        verify(shortCodeGenerator, never()).generate(anyString());
        verify(urlMappingRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void integrityErrorThatIsNotACollisionIsRethrownWithoutRetry() {
        when(shortCodeGenerator.generate(URL)).thenReturn("aaaaaaa");
        when(urlMappingRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("value too long for column"));
        // existsByShortCode("aaaaaaa") -> false (Mockito default): the code is free, so it was some other error

        assertThatThrownBy(() -> service.shortenUrl(request(URL, null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(urlMappingRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void customCodeWithNonCollisionIntegrityErrorIsNotReportedAsConflict() {
        when(urlMappingRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("value too long for column"));
        // existsByShortCode("promo1") -> false both times: the code is still free

        assertThatThrownBy(() -> service.shortenUrl(request(URL, "promo1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void givesUpWithRetryableErrorAfterMaxAttempts() {
        when(shortCodeGenerator.generate(URL)).thenReturn("c1", "c2", "c3");
        when(urlMappingRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate short_code"));
        when(urlMappingRepository.existsByShortCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.shortenUrl(request(URL, null)))
                .isInstanceOf(ShortCodeGenerationException.class);

        verify(urlMappingRepository, times(UrlShortenerService.MAX_SAVE_ATTEMPTS)).saveAndFlush(any());
    }

    private static ShortenUrlRequest request(String url, String customCode) {
        return ShortenUrlRequest.builder().url(url).customCode(customCode).build();
    }
}
