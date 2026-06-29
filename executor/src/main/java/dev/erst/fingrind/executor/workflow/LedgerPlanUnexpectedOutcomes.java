package dev.erst.fingrind.executor.workflow;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Unexpected runtime failures wrapped into ledger-plan journal entries. */
public final class LedgerPlanUnexpectedOutcomes {
  private LedgerPlanUnexpectedOutcomes() {}

  /** Wraps one unexpected step exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedExecutionFailure(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, RuntimeException failure) {
    return LedgerPlanUnexpectedFailureMapper.unexpectedExecutionFailure(
        step, startedAt, finishedAt, failure);
  }

  /** Wraps one unexpected plan-boundary exception into the local rejected journal-entry form. */
  public static BookWorkflowJournalEntry.Rejected unexpectedPlanFailure(
      BookWorkflowBoundaryCheckpoint checkpoint,
      Instant startedAt,
      Instant finishedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor,
      RuntimeException failure,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    return LedgerPlanUnexpectedFailureMapper.unexpectedPlanFailure(
        checkpoint,
        startedAt,
        finishedAt,
        triggerStepId,
        triggerDescriptor,
        failure,
        cleanupFailure,
        priorFailure);
  }
}
