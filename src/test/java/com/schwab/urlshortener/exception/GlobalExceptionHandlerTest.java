package com.schwab.urlshortener.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void customCodeConflictIs409() {
        var response = handler.handleShortCodeConflict(new ShortCodeConflictException("promo1"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Custom code already exists: promo1");
    }

    @Test
    void exhaustedCodeGenerationIs503AndDoesNotLeakInternals() {
        var response = handler.handleShortCodeGeneration(
                new ShortCodeGenerationException(3, new RuntimeException("SQL detail")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("message").toString()).doesNotContain("SQL");
    }

    @Test
    void malformedJsonIs400() {
        var response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void springErrorResponsesKeepTheirStatus() {
        var response = handler.handleGenericException(new HttpRequestMethodNotSupportedException("DELETE"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void unexpectedErrorsAre500WithoutInternals() {
        var response = handler.handleGenericException(new IllegalStateException("db password is hunter2"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message").toString()).doesNotContain("hunter2");
    }
}
