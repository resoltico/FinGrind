package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** Canonical AI-agent-first plan containing ordered ledger steps. */
public record LedgerPlan(
    String planId, LedgerExecutionPolicy executionPolicy, List<LedgerStep> steps) {
  /** Validates one ledger plan. */
  public LedgerPlan {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(executionPolicy, "executionPolicy");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    if (planId.isBlank()) {
      throw new IllegalArgumentException("Ledger planId must not be blank.");
    }
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Ledger plan must contain at least one step.");
    }
    if (steps.stream().map(LedgerStep::stepId).distinct().count() != steps.size()) {
      throw new IllegalArgumentException("Ledger plan stepId values must be unique.");
    }
  }
}
