package com.schwab.urlshortener.orchestration.agent;

import java.util.List;

/**
 * Outcome of validating an agent's output.
 *
 * <ul>
 *   <li>errors not empty: the output is broken. The engine retries, feeding these
 *       messages back to the model, so each one names exactly what to fix.</li>
 *   <li>errors empty, needsHuman: the output is valid, but a person must decide
 *       before the run continues (for example, a HIGH impact ambiguity).</li>
 *   <li>errors empty, no needsHuman: valid, the run continues.</li>
 * </ul>
 */
public record ValidationResult(List<String> errors, boolean needsHuman) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
