package com.schwab.urlshortener.orchestration.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StateTransitionRepository extends JpaRepository<StateTransition, UUID> {
    List<StateTransition> findByRunIdOrderByOccurredAtAsc(UUID runId);   // the audit trail
    List<StateTransition> findByTaskIdOrderByOccurredAtAsc(UUID taskId);
}
