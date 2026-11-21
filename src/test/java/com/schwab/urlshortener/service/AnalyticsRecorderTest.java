package com.schwab.urlshortener.service;

import com.schwab.urlshortener.dto.ClickInfo;
import com.schwab.urlshortener.entity.AnalyticsEvent;
import com.schwab.urlshortener.repository.AnalyticsEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsRecorderTest {

    @Mock AnalyticsEventRepository analyticsEventRepository;
    @InjectMocks AnalyticsRecorder recorder;

    @Test
    void savesEventBuiltFromClickInfo() {
        recorder.record(new ClickInfo("abc1234", "10.0.0.1", "JUnit", "https://ref.example.com"));

        ArgumentCaptor<AnalyticsEvent> saved = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(analyticsEventRepository).save(saved.capture());
        assertThat(saved.getValue().getShortCode()).isEqualTo("abc1234");
        assertThat(saved.getValue().getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(saved.getValue().getReferer()).isEqualTo("https://ref.example.com");
    }

    @Test
    void neverPropagatesFailures() {
        when(analyticsEventRepository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatCode(() -> recorder.record(new ClickInfo("abc1234", null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void clickInfoPrefersForwardedHeadersOverRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        assertThat(ClickInfo.from("c", request).ipAddress()).isEqualTo("10.0.0.9");

        request.addHeader("X-Real-IP", "203.0.113.5");
        assertThat(ClickInfo.from("c", request).ipAddress()).isEqualTo("203.0.113.5");

        request.addHeader("X-Forwarded-For", "198.51.100.7");
        assertThat(ClickInfo.from("c", request).ipAddress()).isEqualTo("198.51.100.7");
    }
}
