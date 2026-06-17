package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
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
    if (steps.size() > ProtocolInteractionLimits.LEDGER_PLAN_STEP_MAX) {
      throw new IllegalArgumentException(
          "Ledger plan must not contain more than "
              + ProtocolInteractionLimits.LEDGER_PLAN_STEP_MAX
              + " steps.");
    }
    if (steps.stream().map(LedgerStep::stepId).distinct().count() != steps.size()) {
      throw new IllegalArgumentException("Ledger plan stepId values must be unique.");
    }
    if (steps.stream().skip(1).anyMatch(LedgerStep.EnsureBook.class::isInstance)) {
      throw new IllegalArgumentException(
          "Ledger plan "
              + LedgerStepKind.ENSURE_BOOK.wireValue()
              + " step must be first when present.");
    }
  }

  /** Returns whether this plan starts by ensuring the selected book exists. */
  public boolean beginsWithEnsureBook() {
    return steps.getFirst() instanceof LedgerStep.EnsureBook;
  }
}
