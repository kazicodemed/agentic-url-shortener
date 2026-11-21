package com.schwab.urlshortener.orchestration.execution;

import com.schwab.urlshortener.orchestration.agent.design.PlannedTask;

import java.util.*;

public final class DagScheduler {

    private DagScheduler() {}

    /** Groups a valid DAG into waves: every task in a wave depends only on earlier waves. */
    public static List<List<PlannedTask>> waves(List<PlannedTask> tasks) {
        // remainingDependencies: id → how many tasks it still waits for
        // dependentsByTaskId:    id → ids of the tasks waiting for it
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, List<String>> dependentsByTaskId = new HashMap<>();
        Map<String, PlannedTask> byId = new HashMap<>();

        // pass 1: every task gets an entry
        for (PlannedTask task : tasks) {
            remainingDependencies.put(task.id(), task.dependsOn().size());
            dependentsByTaskId.put(task.id(), new ArrayList<>());
            byId.put(task.id(), task);
        }

        // pass 2: fill in "who is waiting on me"
        for (PlannedTask task : tasks) {
            for (String dependencyId : task.dependsOn()) {
                dependentsByTaskId.get(dependencyId).add(task.id());
            }
        }

        List<String> ready = new ArrayList<>();

        // first wave: tasks that depend on nothing
        // (a for loop, not forEach: `ready` is reassigned below, so a lambda can't use it)
        for (Map.Entry<String, Integer> entry : remainingDependencies.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        Collections.sort(ready);

        List<List<PlannedTask>> result = new ArrayList<>();

        while (!ready.isEmpty()) {
            List<String> wave = ready;
            List<String> nextReady = new ArrayList<>();

            // Save this wave, then make tasks whose dependencies are all satisfied ready.
            result.add(wave.stream().map(byId::get).toList());

            for (String completedId : wave) {
                for (String dependentId : dependentsByTaskId.get(completedId)) {
                    int remaining = remainingDependencies.get(dependentId) - 1;
                    remainingDependencies.put(dependentId, remaining);

                    if (remaining == 0) {
                        nextReady.add(dependentId);
                    }
                }
            }

            Collections.sort(nextReady);
            ready = nextReady;
        }
        return result;
    }
}
