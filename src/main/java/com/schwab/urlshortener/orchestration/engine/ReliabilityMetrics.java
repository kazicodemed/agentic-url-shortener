package com.schwab.urlshortener.orchestration.engine;

/**
 * Reliability metrics the brief asks for, computed from persisted runs (so they survive restarts
 * and agree with the audit trail). Rates are 0..1; durations are in seconds; null = no data yet.
 *
 * @param successRate            completed / (completed + failed)
 * @param retryRate              stage tasks that needed more than one attempt / stage tasks that ran
 * @param rollbackFrequency      finished runs that rolled work back / finished runs
 * @param mttrSeconds            mean time to recovery: a FAILED run until the next COMPLETED run
 *                               of the same requirement
 * @param avgEndToEndSeconds     mean time from run created to COMPLETED/FAILED (includes human wait)
 */
public record ReliabilityMetrics(
        long totalRuns,
        long completed,
        long failed,
        long awaitingHuman,
        Double successRate,
        Double retryRate,
        Double rollbackFrequency,
        Double mttrSeconds,
        Double avgEndToEndSeconds) {
}
