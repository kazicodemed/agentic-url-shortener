package com.schwab.urlshortener.orchestration.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskExecutionRepository extends JpaRepository<TaskExecution, UUID> {
    List<TaskExecution> findByRunId(UUID runId);
}
