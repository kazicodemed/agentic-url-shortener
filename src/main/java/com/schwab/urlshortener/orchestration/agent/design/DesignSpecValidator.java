package com.schwab.urlshortener.orchestration.agent.design;

import com.schwab.urlshortener.orchestration.agent.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DesignSpecValidator {

    static final int MAX_TASKS = 10;

    public ValidationResult validate(DesignSpec spec) {
        List<String> errors = new ArrayList<>();

        // 1. approach blank? tasks null / empty? → return early: every later check loops over tasks
        if (spec.approach() == null || spec.approach().isBlank()) {
            errors.add("approach is missing: describe the overall design in one or two sentences");
        }

        List<PlannedTask> plannedTasks = spec.tasks();
        if (plannedTasks == null || plannedTasks.isEmpty()) {
            errors.add("tasks is missing or empty: return at least one task");
            return new ValidationResult(errors, false);
        }

        // 2. per task: null entry, blank id, duplicate id, blank title, null dependsOn
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < plannedTasks.size(); i++) {
            PlannedTask plannedTask = plannedTasks.get(i);
            if (plannedTask == null) {
                errors.add("tasks[" + i + "] must not be null");
                continue;                       // nothing else to check on a null task
            }

            if (plannedTask.id() == null || plannedTask.id().isBlank()) {
                errors.add("tasks[" + i + "].id must not be blank");
            } else if (!ids.add(plannedTask.id())) {   // add() returns false → seen before
                errors.add("tasks[" + i + "]: duplicate task id " + plannedTask.id());
            }
            if (plannedTask.title() == null || plannedTask.title().isBlank()) {
                errors.add("tasks[" + i + "].title must not be blank");
            }
            if (plannedTask.dependsOn() == null) {
                errors.add("tasks[" + i + "].dependsOn must not be null; use [] if there are none");
            }
        }

        // 3. too many tasks
        if (plannedTasks.size() > MAX_TASKS) {
            errors.add("too many tasks: " + plannedTasks.size() + " (max " + MAX_TASKS + "); merge smaller steps");
        }

        // 5. unknown dependsOn    (hint: second loop — check each dep against the full Set of ids)

        for (int i = 0; i < plannedTasks.size(); i++){
            PlannedTask t = plannedTasks.get(i);
            if (t == null || t.dependsOn() == null) continue;
            for(String dep: t.dependsOn()){
                // if dep is not in ids → "tasks[i] (T2) depends on unknown task T7"
                if (dep.equals(t.id())) {
                    errors.add("tasks[" + i + "] (" + t.id() + ") depends on itself");
                } else if (!ids.contains(dep)) {
                    errors.add("tasks[" + i + "] (" + t.id() + ") depends on unknown task " + dep);
                }
            }
        }
        // 6. only if errors is still empty → Kahn; if not all tasks come out → cycle error

        if (errors.isEmpty()) {
            List<String> stuck = tasksStuckInCycle(plannedTasks);
            if (!stuck.isEmpty()) {
                errors.add("cycle among tasks: " + String.join(", ", stuck) + "; remove one dependency to break it");
            }
        }
        return new ValidationResult(errors, false);   // design never needs a human
    }

    private List<String> tasksStuckInCycle(List<PlannedTask> tasks)    {
        Map<String, Integer> remainingDependencies = new HashMap<>();
        Map<String, List<String>> dependentsByTaskId = new HashMap<>();

        for (PlannedTask task : tasks ){
            remainingDependencies.put(task.id(), task.dependsOn().size());
            dependentsByTaskId.put(task.id(), new ArrayList<>());
        }

        for (PlannedTask task: tasks) {
            for (String dependencyId : task.dependsOn()) {
                dependentsByTaskId.get(dependencyId).add(task.id());
            }
        }

        Queue<String> ready = new ArrayDeque<>();
        remainingDependencies.forEach((taskId, count) -> {
            if (count == 0) {
                //Node that has is not dependent on any other node
                ready.add(taskId);
            }
        });

        /**
         * This loop processes tasks in dependency order, as part of Kahn’s algorithm for detecting cycles:
         * •
         * ready holds task IDs whose dependencies are all complete.
         * •
         * ready.remove() takes one such task. completedTaskId represents a task we can now consider finished.
         * •
         * dependentsByTaskId.get(completedTaskId) finds the tasks that depend on it.
         * •
         * For each dependent, remainingDependencies.compute(...) reduces its count of unfinished dependencies by one.
         * •
         * When that count reaches 0, all its dependencies are complete, so it’s added to ready and can be processed next.
         */

        while (!ready.isEmpty()) {
            String completedTaskId = ready.remove();
            for (String dependentId : dependentsByTaskId.get(completedTaskId)) {
                int remaining = remainingDependencies.compute(dependentId,
                        (id, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(dependentId);
                }
            }
        }


        return remainingDependencies.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()                       // HashMap has no order; keep messages stable
                .toList();

    }

}
