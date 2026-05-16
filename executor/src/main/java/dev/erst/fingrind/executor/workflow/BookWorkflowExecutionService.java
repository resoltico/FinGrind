package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes local workflow plans against one atomic book session. */
public final class BookWorkflowExecutionService {
  /** Immutable context for one plan-boundary failure that must produce a terminal journal entry. */
  private record BoundaryFailureContext(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowBoundaryPhase phase,
      Instant phaseStartedAt,
      @Nullable BookWorkflowStepId triggerStepId,
      @Nullable BookWorkflowJournalDescriptor triggerDescriptor) {
    private BoundaryFailureContext {
      Objects.requireNonNull(planId, "planId");
      Objects.requireNonNull(planStartedAt, "planStartedAt");
      Objects.requireNonNull(entries, "entries");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(phaseStartedAt, "phaseStartedAt");
    }

    private static BoundaryFailureContext begin(
        BookWorkflowPlanId planId, Instant planStartedAt, List<BookWorkflowJournalEntry> entries) {
      return new BoundaryFailureContext(
          planId,
          planStartedAt,
          entries,
          BookWorkflowBoundaryPhase.BEGIN,
          planStartedAt,
          null,
          null);
    }

    private static BoundaryFailureContext beforeStep(
        BookWorkflowPlanId planId,
        Instant planStartedAt,
        List<BookWorkflowJournalEntry> entries,
        BookWorkflowBoundaryPhase phase,
        BookWorkflowStep step,
        Instant phaseStartedAt) {
      Objects.requireNonNull(step, "step");
      return new BoundaryFailureContext(
          planId,
          planStartedAt,
          entries,
          phase,
          phaseStartedAt,
          step.stepId(),
          new BookWorkflowJournalDescriptor.Step(step));
    }

    private static BoundaryFailureContext afterJournalEntry(
        BookWorkflowPlanId planId,
        Instant planStartedAt,
        List<BookWorkflowJournalEntry> entries,
        BookWorkflowBoundaryPhase phase,
        BookWorkflowJournalEntry entry,
        Instant phaseStartedAt) {
      Objects.requireNonNull(entry, "entry");
      return new BoundaryFailureContext(
          planId,
          planStartedAt,
          entries,
          phase,
          phaseStartedAt,
          entry.stepId(),
          entry.descriptor());
    }
  }

  private final LedgerPlanTransaction transactionStore;
  private final Clock clock;
  private final LedgerPlanStepExecutor stepExecutor;

  /** Creates one local workflow execution service. */
  public BookWorkflowExecutionService(
      LedgerPlanTransaction transactionStore,
      BookAdministrationStore administrationStore,
      BookkeepingReadStore readStore,
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.transactionStore = Objects.requireNonNull(transactionStore, "transactionStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.stepExecutor =
        new LedgerPlanStepExecutor(
            Objects.requireNonNull(administrationStore, "administrationStore"),
            Objects.requireNonNull(readStore, "readStore"),
            Objects.requireNonNull(validationStore, "validationStore"),
            Objects.requireNonNull(commitStore, "commitStore"),
            postingIdGenerator,
            Objects.requireNonNull(clock));
  }

  /** Executes one local workflow plan atomically. */
  public BookWorkflowExecutionResult execute(BookWorkflowPlan plan) {
    Objects.requireNonNull(plan, "plan");
    Instant startedAt = Instant.now(clock);
    List<BookWorkflowJournalEntry> entries = new ArrayList<>();
    List<BookWorkflowStep> steps = plan.steps();
    BookWorkflowStep firstStep = steps.getFirst();

    try {
      transactionStore.beginLedgerPlanTransaction();
    } catch (RuntimeException exception) {
      return boundaryFailureResult(
          BoundaryFailureContext.begin(plan.planId(), startedAt, entries), exception, null, null);
    }

    if (!plan.beginsWithOpenBook()) {
      try {
        if (!stepExecutor.inspectBook().allowsInitializedWorkflow()) {
          return failedResultWithRollback(
              plan.planId(),
              startedAt,
              entries,
              stepExecutor.missingBookEntry(firstStep, startedAt));
        }
      } catch (RuntimeException exception) {
        return boundaryFailureAfterRollback(
            BoundaryFailureContext.beforeStep(
                plan.planId(),
                startedAt,
                entries,
                BookWorkflowBoundaryPhase.INITIALIZATION_CHECK,
                firstStep,
                Instant.now(clock)),
            exception,
            null);
      }
    }

    BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep = null;
    for (BookWorkflowStep step : steps) {
      Instant stepStartedAt = Instant.now(clock);
      BookWorkflowJournalEntry stepEntry;
      try {
        stepEntry = stepExecutor.execute(step);
      } catch (RuntimeException exception) {
        if (pendingSuccessfulStep != null) {
          entries.add(pendingSuccessfulStep);
        }
        RuntimeException rollbackFailure = rollbackFailure();
        if (rollbackFailure != null) {
          BookWorkflowFailure priorFailure =
              LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                      step, stepStartedAt, Instant.now(clock), exception)
                  .requiredFailure();
          return boundaryFailureResult(
              BoundaryFailureContext.afterJournalEntry(
                  plan.planId(),
                  startedAt,
                  entries,
                  BookWorkflowBoundaryPhase.ROLLBACK,
                  LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                      step, stepStartedAt, Instant.now(clock), exception),
                  Instant.now(clock)),
              rollbackFailure,
              null,
              priorFailure);
        }
        entries.add(
            LedgerPlanOutcomeMapper.unexpectedExecutionFailure(
                step, stepStartedAt, Instant.now(clock), exception));
        return result(plan.planId(), BookWorkflowExecutionStatus.REJECTED, startedAt, entries);
      }
      switch (stepEntry) {
        case BookWorkflowJournalEntry.Succeeded succeeded -> {
          if (pendingSuccessfulStep != null) {
            entries.add(pendingSuccessfulStep);
          }
          pendingSuccessfulStep = succeeded;
        }
        case BookWorkflowJournalEntry.Failed failed -> {
          if (pendingSuccessfulStep != null) {
            entries.add(pendingSuccessfulStep);
          }
          return failedResultWithRollback(plan.planId(), startedAt, entries, failed);
        }
      }
    }

    Objects.requireNonNull(pendingSuccessfulStep, "pendingSuccessfulStep");
    Instant commitStartedAt = Instant.now(clock);
    try {
      transactionStore.commitLedgerPlanTransaction();
    } catch (RuntimeException exception) {
      return boundaryFailureAfterRollback(
          BoundaryFailureContext.afterJournalEntry(
              plan.planId(),
              startedAt,
              entries,
              BookWorkflowBoundaryPhase.COMMIT,
              pendingSuccessfulStep,
              commitStartedAt),
          exception,
          null);
    }
    entries.add(pendingSuccessfulStep);
    return result(plan.planId(), BookWorkflowExecutionStatus.SUCCEEDED, startedAt, entries);
  }

  private BookWorkflowExecutionResult failedResultWithRollback(
      BookWorkflowPlanId planId,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.Failed failed) {
    RuntimeException rollbackFailure = rollbackFailure();
    if (rollbackFailure != null) {
      return boundaryFailureResult(
          BoundaryFailureContext.afterJournalEntry(
              planId,
              startedAt,
              entries,
              BookWorkflowBoundaryPhase.ROLLBACK,
              failed,
              Instant.now(clock)),
          rollbackFailure,
          null,
          failed.requiredFailure());
    }
    entries.add(failed);
    return result(planId, failedStatus(failed), startedAt, entries);
  }

  private BookWorkflowExecutionResult boundaryFailureAfterRollback(
      BoundaryFailureContext context,
      RuntimeException exception,
      @Nullable BookWorkflowFailure priorFailure) {
    RuntimeException rollbackFailure = rollbackFailure();
    return boundaryFailureResult(context, exception, rollbackFailure, priorFailure);
  }

  private BookWorkflowExecutionResult boundaryFailureResult(
      BoundaryFailureContext context,
      RuntimeException exception,
      @Nullable RuntimeException cleanupFailure,
      @Nullable BookWorkflowFailure priorFailure) {
    context
        .entries()
        .add(
            LedgerPlanOutcomeMapper.unexpectedPlanFailure(
                context.phase(),
                context.phaseStartedAt(),
                Instant.now(clock),
                context.triggerStepId(),
                context.triggerDescriptor(),
                exception,
                cleanupFailure,
                priorFailure));
    return result(
        context.planId(),
        BookWorkflowExecutionStatus.REJECTED,
        context.planStartedAt(),
        context.entries());
  }

  private @Nullable RuntimeException rollbackFailure() {
    try {
      transactionStore.rollbackLedgerPlanTransaction();
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

  private BookWorkflowExecutionResult result(
      BookWorkflowPlanId planId,
      BookWorkflowExecutionStatus status,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries) {
    return new BookWorkflowExecutionResult(
        planId,
        status,
        new BookWorkflowExecutionJournal(startedAt, Instant.now(clock), List.copyOf(entries)));
  }
}
