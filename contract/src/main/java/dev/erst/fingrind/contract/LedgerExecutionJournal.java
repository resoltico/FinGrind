package dev.erst.fingrind.contract;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Structured execution journal emitted for every ledger-plan run. */
public record LedgerExecutionJournal(
    Instant startedAt, Instant finishedAt, List<LedgerJournalEntry> steps) {
  /** Validates one plan execution journal. */
  public LedgerExecutionJournal {
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    steps = steps == null ? List.of() : List.copyOf(steps);
    if (steps.isEmpty()) {
      throw new IllegalArgumentException("Ledger journal must contain at least one step.");
    }
    LedgerJournalEntry terminalStep = steps.getLast();
    List<LedgerJournalEntry> priorSteps = steps.subList(0, steps.size() - 1);
    switch (terminalStep) {
      case LedgerJournalEntry.Succeeded _ -> {
        if (!steps.stream().allMatch(LedgerExecutionJournal::succeededStep)) {
          throw new IllegalArgumentException(
              "Succeeded ledger journals may contain only succeeded steps.");
        }
      }
      case LedgerJournalEntry.Failed _ -> {
        if (!priorSteps.stream().allMatch(LedgerExecutionJournal::succeededStep)) {
          throw new IllegalArgumentException(
              "Failed ledger journals may contain succeeded steps only before the terminal failure.");
        }
      }
    }
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("Ledger journal finishedAt must not precede startedAt.");
    }
  }

  /** Returns the terminal step that determined the final plan outcome. */
  public LedgerJournalEntry terminalStep() {
    return steps.getLast();
  }

  /** Returns the stable public plan status derived from the terminal journal step. */
  public LedgerPlanStatus status() {
    return switch (terminalStep()) {
      case LedgerJournalEntry.Succeeded _ -> LedgerPlanStatus.SUCCEEDED;
      case LedgerJournalEntry.Rejected _ -> LedgerPlanStatus.REJECTED;
      case LedgerJournalEntry.AssertionFailed _ -> LedgerPlanStatus.ASSERTION_FAILED;
    };
  }

  /** Returns the required failed terminal step for rejected or assertion-failed plans. */
  public LedgerJournalEntry.Failed requiredFailedStep() {
    return switch (terminalStep()) {
      case LedgerJournalEntry.Succeeded _ ->
          throw new IllegalStateException("Succeeded ledger journals do not have a failed step.");
      case LedgerJournalEntry.Failed failed -> failed;
    };
  }

  private static boolean succeededStep(LedgerJournalEntry step) {
    return switch (step) {
      case LedgerJournalEntry.Succeeded _ -> true;
      case LedgerJournalEntry.Failed _ -> false;
    };
  }
}
