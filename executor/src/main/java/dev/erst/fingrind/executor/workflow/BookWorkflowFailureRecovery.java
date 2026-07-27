package dev.erst.fingrind.executor.workflow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Coordinates rollback and journaled results for failed workflow execution. */
final class BookWorkflowFailureRecovery {
  private final Clock clock;
  private final BookWorkflowExecutionResultFactory resultFactory;
  private final Runnable rollback;

  BookWorkflowFailureRecovery(
      Clock clock, BookWorkflowExecutionResultFactory resultFactory, Runnable rollback) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.resultFactory = Objects.requireNonNull(resultFactory, "resultFactory");
    this.rollback = Objects.requireNonNull(rollback, "rollback");
  }

  void appendPendingSuccess(
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.@Nullable Succeeded pendingSuccessfulStep) {
    if (pendingSuccessfulStep != null) {
      entries.add(pendingSuccessfulStep);
    }
  }

  BookWorkflowExecutionResult unexpectedStepFailure(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowStep step,
      Instant stepStartedAt,
      RuntimeException exception) {
    BookWorkflowJournalEntry.Failed unexpectedFailure =
        LedgerPlanUnexpectedOutcomes.unexpectedExecutionFailure(
            step, stepStartedAt, Instant.now(clock), exception);
    RuntimeException rollbackFailure = rollbackFailure();
    if (rollbackFailure != null) {
      return boundaryFailureResult(
          BookWorkflowBoundaryFailureContext.afterJournalEntry(
              planId,
              planStartedAt,
              entries,
              BookWorkflowBoundaryCheckpoint.ROLLBACK,
              unexpectedFailure,
              Instant.now(clock)),
          rollbackFailure,
          null,
          unexpectedFailure.requiredFailure());
    }
    entries.add(unexpectedFailure);
    return resultFactory.result(
        planId, BookWorkflowExecutionStatus.REJECTED, planStartedAt, entries);
  }

  BookWorkflowExecutionResult failedResultWithRollback(
      BookWorkflowPlanId planId,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.Failed failed) {
    RuntimeException rollbackFailure = rollbackFailure();
    if (rollbackFailure != null) {
      return boundaryFailureResult(
          BookWorkflowBoundaryFailureContext.afterJournalEntry(
              planId,
              startedAt,
              entries,
              BookWorkflowBoundaryCheckpoint.ROLLBACK,
              failed,
              Instant.now(clock)),
          rollbackFailure,
          null,
          failed.requiredFailure());
    }
    entries.add(failed);
    return resultFactory.result(planId, failedStatus(failed), startedAt, entries);
  }

  BookWorkflowExecutionResult boundaryFailureAfterRollback(
      BookWorkflowBoundaryFailureContext context,
      RuntimeException exception,
      @Nullable BookWorkflowFailure priorFailure) {
    RuntimeException rollbackFailure = rollbackFailure();
    return boundaryFailureResult(context, exception, rollbackFailure, priorFailure);
  }

  BookWorkflowExecutionResult boundaryFailureResult(
      BookWorkflowBoundaryFailureContext context,
      RuntimeException exception,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    context
        .entries()
        .add(
            LedgerPlanUnexpectedOutcomes.unexpectedPlanFailure(
                context.checkpoint(),
                context.checkpointStartedAt(),
                Instant.now(clock),
                context.triggerStepId(),
                context.triggerDescriptor(),
                exception,
                cleanupFailure,
                priorFailure));
    return resultFactory.result(
        context.planId(),
        BookWorkflowExecutionStatus.REJECTED,
        context.planStartedAt(),
        context.entries());
  }

  void preserveFailureAfterRollback(RuntimeException exception) {
    RuntimeException rollbackFailure = rollbackFailure();
    if (rollbackFailure != null) {
      exception.addSuppressed(rollbackFailure);
    }
  }

  private @Nullable RuntimeException rollbackFailure() {
    try {
      rollback.run();
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private static BookWorkflowExecutionStatus failedStatus(BookWorkflowJournalEntry.Failed failed) {
    return switch (failed) {
      case BookWorkflowJournalEntry.Rejected _ -> BookWorkflowExecutionStatus.REJECTED;
      case BookWorkflowJournalEntry.AssertionFailed _ ->
          BookWorkflowExecutionStatus.ASSERTION_FAILED;
    };
  }
}
