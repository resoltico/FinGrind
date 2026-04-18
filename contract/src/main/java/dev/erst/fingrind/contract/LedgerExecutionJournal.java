package dev.erst.fingrind.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Structured execution journal emitted for every ledger-plan run. */
public record LedgerExecutionJournal(
    String planId,
    LedgerPlanStatus status,
    Instant startedAt,
    Instant finishedAt,
    List<LedgerJournalEntry> steps) {
  /** Validates one plan execution journal. */
  public LedgerExecutionJournal {
    Objects.requireNonNull(planId, "planId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    if (planId.isBlank()) {
      throw new IllegalArgumentException("Ledger journal planId must not be blank.");
    }
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Ledger journal must contain at least one step.");
    }
    if (status == LedgerPlanStatus.SUCCEEDED
        && steps.stream().anyMatch(step -> !(step instanceof LedgerJournalEntry.Succeeded))) {
      throw new IllegalArgumentException(
          "Succeeded ledger journals may contain only succeeded steps.");
    }
    if (status == LedgerPlanStatus.REJECTED
        && !(steps.getLast() instanceof LedgerJournalEntry.Rejected)) {
      throw new IllegalArgumentException("Rejected ledger journals must end on a rejected step.");
    }
    if (status == LedgerPlanStatus.ASSERTION_FAILED
        && !(steps.getLast() instanceof LedgerJournalEntry.AssertionFailed)) {
      throw new IllegalArgumentException(
          "Assertion-failed ledger journals must end on an assertion-failed step.");
    }
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("Ledger journal finishedAt must not precede startedAt.");
    }
  }

  /** Returns the terminal step that determined the final plan outcome. */
  public LedgerJournalEntry terminalStep() {
    return steps.getLast();
  }

  /** Returns the required failed terminal step for rejected or assertion-failed plans. */
  public LedgerJournalEntry.Failed requiredFailedStep() {
    if (status == LedgerPlanStatus.SUCCEEDED) {
      throw new IllegalStateException("Succeeded ledger journals do not have a failed step.");
    }
    return (LedgerJournalEntry.Failed) terminalStep();
  }
}
