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
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("Ledger journal finishedAt must not precede startedAt.");
    }
  }
}
