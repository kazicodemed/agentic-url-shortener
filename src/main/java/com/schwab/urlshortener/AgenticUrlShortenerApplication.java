package com.schwab.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class AgenticUrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticUrlShortenerApplication.class, args);
    }
}
