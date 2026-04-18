package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Core-owned ledger-plan execution semantics advertised to AI-agent callers. */
public record PlanExecutionFacts(
    String transactionMode, String failurePolicy, String journal, List<String> hardLimitations) {
  /** Validates plan-execution metadata. */
  public PlanExecutionFacts {
    Objects.requireNonNull(transactionMode, "transactionMode");
    Objects.requireNonNull(failurePolicy, "failurePolicy");
    Objects.requireNonNull(journal, "journal");
    hardLimitations = List.copyOf(Objects.requireNonNull(hardLimitations, "hardLimitations"));
    if (transactionMode.isBlank()) {
      throw new IllegalArgumentException("Plan transaction mode must not be blank.");
    }
    if (failurePolicy.isBlank()) {
      throw new IllegalArgumentException("Plan failure policy must not be blank.");
    }
    if (journal.isBlank()) {
      throw new IllegalArgumentException("Plan journal description must not be blank.");
    }
  }
}
