package com.schwab.urlshortener.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for Virtual Threads (Java 21+)
 *
 * Virtual threads are lightweight threads that significantly improve throughput
 * for I/O-bound workloads like database queries, cache operations, and HTTP calls.
 *
 * Tomcat's request threads are switched to virtual threads by
 * {@code spring.threads.virtual.enabled: true} in application.yml.
 *
 * This class only defines the named executors our own code asks for, each with
 * its own thread-name prefix so logs show which part of the system a thread
 * belongs to. Because we define our own executors, Spring Boot does not create
 * its default "applicationTaskExecutor". Nothing here needs one: every
 * {@code @Async} in this codebase must name its executor explicitly, e.g.
 * {@code @Async("analyticsExecutor")}.
 */
@Configuration
@EnableAsync
@Slf4j
public class VirtualThreadConfig {

    /**
     * Executor for orchestration tasks.
     * Will be used by the orchestration engine for parallel agent execution.
     * Unbounded on purpose: threads are cheap; scarce resources (LLM calls) are
     * bounded separately by AgentConcurrencyLimiter.
     */
    @Bean(name = "orchestrationExecutor")
    public AsyncTaskExecutor orchestrationExecutor() {
        log.info("Creating virtual thread executor for orchestration layer");
        var executor = new SimpleAsyncTaskExecutor("orchestration-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * Executor for analytics processing: used by {@code @Async("analyticsExecutor")}
     * in AnalyticsRecorder, keeping analytics writes off the redirect path.
     */
    @Bean(name = "analyticsExecutor")
    public AsyncTaskExecutor analyticsExecutor() {
        log.info("Creating virtual thread executor for analytics processing");
        var executor = new SimpleAsyncTaskExecutor("analytics-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
