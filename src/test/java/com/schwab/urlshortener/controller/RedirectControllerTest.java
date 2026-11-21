package com.schwab.urlshortener.controller;

import com.schwab.urlshortener.exception.GlobalExceptionHandler;
import com.schwab.urlshortener.exception.UrlNotFoundException;
import com.schwab.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the redirect route. Before the fix, the redirect lived at
 * /api/v1/{shortCode}, so the advertised short URL (baseUrl/{shortCode}) failed,
 * and the catch-all exception handler turned that 404 into a 500.
 */
class RedirectControllerTest {

    private UrlShortenerService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(UrlShortenerService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RedirectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shortUrlAtRootRedirectsWith302() throws Exception {
        when(service.resolveUrl(eq("schwab"), any())).thenReturn("https://www.schwab.com");

        mvc.perform(get("/schwab"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.schwab.com"));
    }

    @Test
    void unknownCodeIs404NotA500() throws Exception {
        when(service.resolveUrl(eq("nosuch1"), any())).thenThrow(new UrlNotFoundException("nosuch1"));

        mvc.perform(get("/nosuch1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void pathsThatCannotBeShortCodesNeverReachTheService() throws Exception {
        mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
        mvc.perform(get("/ab")).andExpect(status().isNotFound());   // too short for a code
        verify(service, never()).resolveUrl(anyString(), any());
    }

    @Test
    void wrongHttpMethodKeepsSpringsStatusInsteadOf500() throws Exception {
        mvc.perform(post("/schwab")).andExpect(status().isMethodNotAllowed());
    }
}
