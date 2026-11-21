package com.schwab.urlshortener.orchestration.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionRecordRepository extends JpaRepository<DecisionRecord, UUID> {
    List<DecisionRecord> findByRunIdOrderByDecidedAtAsc(UUID uuid);
}
