package com.schwab.urlshortener.orchestration.run;

public enum TaskStatus {
    PENDING, RUNNING, AWAITING_HUMAN, COMPLETED, FAILED,
    /** never started: an earlier task in the plan failed (safe-stop) */
    SKIPPED,
    /** had completed, then was undone because a later task in the same run failed */
    ROLLED_BACK
}
