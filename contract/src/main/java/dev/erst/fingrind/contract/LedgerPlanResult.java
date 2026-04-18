package dev.erst.fingrind.contract;

import java.util.Objects;

/** Top-level result family for executing one canonical ledger plan. */
public sealed interface LedgerPlanResult
    permits LedgerPlanResult.Succeeded,
        LedgerPlanResult.Rejected,
        LedgerPlanResult.AssertionFailed {
  /** Returns the caller-visible plan identifier. */
  String planId();

  /** Returns the structured execution journal for this plan run. */
  LedgerExecutionJournal journal();

  /** Returns the stable public status for this plan result. */
  LedgerPlanStatus status();

  /** Successful plan result that committed the atomic transaction. */
  record Succeeded(String planId, LedgerExecutionJournal journal) implements LedgerPlanResult {
    /** Validates one succeeded plan result. */
    public Succeeded {
      require(planId, journal, LedgerPlanStatus.SUCCEEDED);
    }

    @Override
    public LedgerPlanStatus status() {
      return LedgerPlanStatus.SUCCEEDED;
    }
  }

  /** Deterministically rejected plan result that rolled back the atomic transaction. */
  record Rejected(String planId, LedgerExecutionJournal journal) implements LedgerPlanResult {
    /** Validates one rejected plan result. */
    public Rejected {
      require(planId, journal, LedgerPlanStatus.REJECTED);
    }

    @Override
    public LedgerPlanStatus status() {
      return LedgerPlanStatus.REJECTED;
    }
  }

  /** Assertion-failed plan result that rolled back the atomic transaction. */
  record AssertionFailed(String planId, LedgerExecutionJournal journal)
      implements LedgerPlanResult {
    /** Validates one assertion-failed plan result. */
    public AssertionFailed {
      require(planId, journal, LedgerPlanStatus.ASSERTION_FAILED);
    }

    @Override
    public LedgerPlanStatus status() {
      return LedgerPlanStatus.ASSERTION_FAILED;
    }
  }

  private static void require(
      String planId, LedgerExecutionJournal journal, LedgerPlanStatus expectedStatus) {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(journal, "journal");
    Objects.requireNonNull(expectedStatus, "expectedStatus");
    if (!planId.equals(journal.planId())) {
      throw new IllegalArgumentException("Ledger plan result planId must match journal planId.");
    }
    if (journal.status() != expectedStatus) {
      throw new IllegalArgumentException("Ledger plan result status must match journal status.");
    }
  }
}
