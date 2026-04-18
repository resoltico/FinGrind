package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import java.util.List;
import java.util.Objects;

/** Canonical AI-agent-first plan containing ordered ledger steps. */
public record LedgerPlan(LedgerPlanId planId, List<LedgerStep> steps) {
  /** Validates one ledger plan. */
  public LedgerPlan {
    Objects.requireNonNull(planId, "planId");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Ledger plan must contain at least one step.");
    }
    if (steps.size() > ProtocolLimits.LEDGER_PLAN_STEP_MAX) {
      throw new IllegalArgumentException(
          "Ledger plan must not contain more than "
              + ProtocolLimits.LEDGER_PLAN_STEP_MAX
              + " steps.");
    }
    if (steps.stream().map(LedgerStep::stepId).distinct().count() != steps.size()) {
      throw new IllegalArgumentException("Ledger plan stepId values must be unique.");
    }
    if (steps.stream().skip(1).anyMatch(LedgerStep.OpenBook.class::isInstance)) {
      throw new IllegalArgumentException("Ledger plan open-book step must be first when present.");
    }
  }

  /** Returns whether this plan starts by initializing the selected book. */
  public boolean beginsWithOpenBook() {
    return steps.getFirst() instanceof LedgerStep.OpenBook;
  }
}
