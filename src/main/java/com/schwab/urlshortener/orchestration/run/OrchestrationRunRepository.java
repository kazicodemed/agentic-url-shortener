package com.schwab.urlshortener.orchestration.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRun, UUID>  {

}
