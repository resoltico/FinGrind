package dev.erst.fingrind.contract;

import java.util.Objects;

/** Top-level result for executing one canonical ledger plan. */
public record LedgerPlanResult(
    String planId, LedgerPlanStatus status, LedgerExecutionJournal journal) {
  /** Validates one ledger-plan result. */
  public LedgerPlanResult {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(journal, "journal");
    if (!planId.equals(journal.planId())) {
      throw new IllegalArgumentException("Ledger plan result planId must match journal planId.");
    }
    if (status != journal.status()) {
      throw new IllegalArgumentException("Ledger plan result status must match journal status.");
    }
  }
}
