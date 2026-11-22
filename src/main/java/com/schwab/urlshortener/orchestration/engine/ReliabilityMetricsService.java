package com.schwab.urlshortener.orchestration.engine;

import com.schwab.urlshortener.orchestration.run.OrchestrationRun;
import com.schwab.urlshortener.orchestration.run.OrchestrationRunRepository;
import com.schwab.urlshortener.orchestration.run.RunStatus;
import com.schwab.urlshortener.orchestration.run.TaskExecution;
import com.schwab.urlshortener.orchestration.run.TaskExecutionRepository;
import com.schwab.urlshortener.orchestration.run.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes {@link ReliabilityMetrics} from the stored runs and tasks.
 *
 * <p>Prototype trade-off: it reads every run. For production volumes this becomes
 * aggregate SQL over a time window (or pre-aggregated counters); the definitions stay the same.
 */
@Service
@RequiredArgsConstructor
public class ReliabilityMetricsService {

    private final OrchestrationRunRepository runRepo;
    private final TaskExecutionRepository taskRepo;

    @Transactional(readOnly = true)
    public ReliabilityMetrics compute() {
        List<OrchestrationRun> runs = runRepo.findAll();
        List<TaskExecution> tasks = taskRepo.findAll();

        long completed = count(runs, RunStatus.COMPLETED);
        long failed = count(runs, RunStatus.FAILED);
        long awaiting = count(runs, RunStatus.AWAITING_HUMAN);
        long finished = completed + failed;

        // retries: only stage tasks (REQUIREMENTS, DESIGN) have a retry loop
        List<TaskExecution> stageTasks = tasks.stream()
                .filter(t -> !t.getTaskType().startsWith(OrchestrationEngine.EXECUTE_PREFIX))
                .filter(t -> t.getAttempts() != null)
                .toList();
        long retried = stageTasks.stream().filter(t -> t.getAttempts() > 1).count();

        // rollbacks: runs that had to undo completed work
        Set<UUID> runsWithRollback = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.ROLLED_BACK)
                .map(t -> t.getRun().getId())
                .collect(Collectors.toSet());

        return new ReliabilityMetrics(
                runs.size(), completed, failed, awaiting,
                ratio(completed, finished),
                ratio(retried, stageTasks.size()),
                ratio(runsWithRollback.size(), finished),
                mttrSeconds(runs),
                avgEndToEndSeconds(runs));
    }

    /** For each requirement: every FAILED run, until the next COMPLETED run of the same requirement. */
    private static Double mttrSeconds(List<OrchestrationRun> runs) {
        Map<String, List<OrchestrationRun>> byRequirement = runs.stream()
                .filter(r -> r.getStatus() == RunStatus.COMPLETED || r.getStatus() == RunStatus.FAILED)
                .collect(Collectors.groupingBy(OrchestrationRun::getRawRequirement));

        List<Double> recoveries = new ArrayList<>();
        for (List<OrchestrationRun> history : byRequirement.values()) {
            history.sort(Comparator.comparing(OrchestrationRun::getUpdatedAt));
            OrchestrationRun firstFailure = null;
            for (OrchestrationRun run : history) {
                if (run.getStatus() == RunStatus.FAILED && firstFailure == null) {
                    firstFailure = run;
                } else if (run.getStatus() == RunStatus.COMPLETED && firstFailure != null) {
                    recoveries.add(seconds(firstFailure.getUpdatedAt(), run.getUpdatedAt()));
                    firstFailure = null;
                }
            }
        }
        return recoveries.isEmpty() ? null : recoveries.stream().mapToDouble(d -> d).average().orElseThrow();
    }

    private static Double avgEndToEndSeconds(List<OrchestrationRun> runs) {
        return runs.stream()
                .filter(r -> r.getStatus() == RunStatus.COMPLETED || r.getStatus() == RunStatus.FAILED)
                .mapToDouble(r -> seconds(r.getCreatedAt(), r.getUpdatedAt()))
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    private static long count(List<OrchestrationRun> runs, RunStatus status) {
        return runs.stream().filter(r -> r.getStatus() == status).count();
    }

    private static Double ratio(long part, long whole) {
        return whole == 0 ? null : (double) part / whole;
    }

    private static double seconds(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return Duration.between(from, to).toMillis() / 1000.0;
    }
}
