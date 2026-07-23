package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Constructs immutable workflow results with one clock-owned completion instant. */
final class BookWorkflowExecutionResultFactory {
  private final Clock clock;

  BookWorkflowExecutionResultFactory(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  BookWorkflowExecutionResult result(
      BookWorkflowPlanId planId,
      BookWorkflowExecutionStatus status,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries) {
    return result(planId, status, startedAt, entries, null);
  }

  BookWorkflowExecutionResult result(
      BookWorkflowPlanId planId,
      BookWorkflowExecutionStatus status,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      @Nullable AttestationCommit attestationCommit) {
    return new BookWorkflowExecutionResult(
        planId,
        status,
        new BookWorkflowExecutionJournal(startedAt, Instant.now(clock), List.copyOf(entries)),
        attestationCommit);
  }
}
