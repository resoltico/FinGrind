package dev.erst.fingrind.contract.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Top-level result family for executing one canonical ledger plan. */
public sealed interface LedgerPlanResult
    permits LedgerPlanResult.Succeeded,
        LedgerPlanResult.Rejected,
        LedgerPlanResult.AssertionFailed {
  /** Returns the caller-visible plan identifier. */
  LedgerPlanId planId();

  /** Returns the structured execution journal for this plan run. */
  LedgerExecutionJournal journal();

  /** Returns the stable public status for this plan result. */
  default LedgerPlanStatus status() {
    return switch (this) {
      case Succeeded _ -> LedgerPlanStatus.SUCCEEDED;
      case Rejected _ -> LedgerPlanStatus.REJECTED;
      case AssertionFailed _ -> LedgerPlanStatus.ASSERTION_FAILED;
    };
  }

  /** Successful plan result that committed the atomic transaction. */
  record Succeeded(
      LedgerPlanId planId,
      LedgerExecutionJournal journal,
      @Nullable AttestationCommit attestationCommit)
      implements LedgerPlanResult {
    /**
     * Validates one succeeded plan result.
     *
     * <p>A {@code null} commitment denotes a read-only successful plan, which appends no aggregate
     * operation.
     */
    public Succeeded {
      require(planId, journal, LedgerPlanStatus.SUCCEEDED);
    }
  }

  /** Deterministically rejected plan result that rolled back the atomic transaction. */
  record Rejected(LedgerPlanId planId, LedgerExecutionJournal journal) implements LedgerPlanResult {
    /** Validates one rejected plan result. */
    public Rejected {
      require(planId, journal, LedgerPlanStatus.REJECTED);
    }
  }

  /** Assertion-failed plan result that rolled back the atomic transaction. */
  record AssertionFailed(LedgerPlanId planId, LedgerExecutionJournal journal)
      implements LedgerPlanResult {
    /** Validates one assertion-failed plan result. */
    public AssertionFailed {
      require(planId, journal, LedgerPlanStatus.ASSERTION_FAILED);
    }
  }

  private static void require(
      LedgerPlanId planId, LedgerExecutionJournal journal, LedgerPlanStatus expectedStatus) {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(journal, "journal");
    Objects.requireNonNull(expectedStatus, "expectedStatus");
    if (journal.status() != expectedStatus) {
      throw new IllegalArgumentException("Ledger plan result status must match journal status.");
    }
  }
}
