package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes local workflow plans against one atomic book session. */
public final class BookWorkflowExecutionService {
  private final LedgerPlanTransaction transactionStore;
  private final Clock clock;
  private final LedgerPlanStepExecutor stepExecutor;

  /** Creates one local workflow execution service. */
  public BookWorkflowExecutionService(
      LedgerPlanTransaction transactionStore,
      BookWorkflowExecutionDependencies dependencies,
      Clock clock) {
    this.transactionStore = Objects.requireNonNull(transactionStore, "transactionStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(dependencies, "dependencies");
    this.stepExecutor =
        new LedgerPlanStepExecutor(
            dependencies.administrationStore(),
            dependencies.accountCatalogStore(),
            dependencies.readStore(),
            dependencies.validationStore(),
            dependencies.commitStore(),
            dependencies.taxAdministrationStore(),
            dependencies.postingIdGenerator(),
            clock);
  }

  /** Executes one local workflow plan atomically. */
  public BookWorkflowExecutionResult execute(BookWorkflowPlan plan) {
    Objects.requireNonNull(plan, "plan");
    Instant startedAt = Instant.now(clock);
    List<BookWorkflowJournalEntry> entries = new ArrayList<>();
    List<BookWorkflowStep> steps = plan.steps();
    BookWorkflowStep firstStep = steps.getFirst();

    BookWorkflowExecutionResult transactionFailure =
        beginTransactionOrReject(plan, startedAt, entries);
    if (transactionFailure != null) {
      return transactionFailure;
    }

    BookWorkflowExecutionResult initializationFailure =
        verifyWorkflowInitialization(plan, startedAt, firstStep, entries);
    if (initializationFailure != null) {
      return initializationFailure;
    }

    BookWorkflowStepExecutionState stepExecutionState =
        executeSteps(plan, startedAt, steps, entries);
    if (stepExecutionState.terminalResult() != null) {
      return Objects.requireNonNull(stepExecutionState.terminalResult(), "terminalResult");
    }
    return commitSuccessfulPlan(
        plan.planId(),
        startedAt,
        entries,
        Objects.requireNonNull(
            stepExecutionState.pendingSuccessfulStep(), "pendingSuccessfulStep"));
  }

  private @Nullable BookWorkflowExecutionResult beginTransactionOrReject(
      BookWorkflowPlan plan, Instant startedAt, List<BookWorkflowJournalEntry> entries) {
    try {
      transactionStore.beginLedgerPlanTransaction();
      return null;
    } catch (RuntimeException exception) {
      return boundaryFailureResult(
          BookWorkflowBoundaryFailureContext.begin(plan.planId(), startedAt, entries),
          exception,
          null,
          null);
    }
  }

  private @Nullable BookWorkflowExecutionResult verifyWorkflowInitialization(
      BookWorkflowPlan plan,
      Instant startedAt,
      BookWorkflowStep firstStep,
      List<BookWorkflowJournalEntry> entries) {
    if (plan.beginsWithEnsureBook()) {
      return null;
    }
    try {
      if (stepExecutor.allowsInitializedWorkflow()) {
        return null;
      }
      return failedResultWithRollback(
          plan.planId(), startedAt, entries, stepExecutor.missingBookEntry(firstStep, startedAt));
    } catch (RuntimeException exception) {
      return boundaryFailureAfterRollback(
          BookWorkflowBoundaryFailureContext.beforeStep(
              plan.planId(),
              startedAt,
              entries,
              BookWorkflowBoundaryCheckpoint.INITIALIZATION_CHECK,
              firstStep,
              Instant.now(clock)),
          exception,
          null);
    }
  }

  private BookWorkflowStepExecutionState executeSteps(
      BookWorkflowPlan plan,
      Instant startedAt,
      List<BookWorkflowStep> steps,
      List<BookWorkflowJournalEntry> entries) {
    BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep = null;
    for (BookWorkflowStep step : steps) {
      var stepOutcome = executeStep(plan.planId(), startedAt, entries, pendingSuccessfulStep, step);
      if (stepOutcome.terminalResult() != null) {
        return stepOutcome;
      }
      pendingSuccessfulStep =
          Objects.requireNonNull(stepOutcome.pendingSuccessfulStep(), "pendingSuccessfulStep");
    }
    return BookWorkflowStepExecutionState.pending(
        Objects.requireNonNull(pendingSuccessfulStep, "pendingSuccessfulStep"));
  }

  private BookWorkflowStepExecutionState executeStep(
      BookWorkflowPlanId planId,
      Instant planStartedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.@Nullable Succeeded pendingSuccessfulStep,
      BookWorkflowStep step) {
    Instant stepStartedAt = Instant.now(clock);
    try {
      BookWorkflowJournalEntry stepEntry = stepExecutor.execute(step);
      return switch (stepEntry) {
        case BookWorkflowJournalEntry.Succeeded succeeded -> {
          appendPendingSuccess(entries, pendingSuccessfulStep);
          yield BookWorkflowStepExecutionState.pending(succeeded);
        }
        case BookWorkflowJournalEntry.Failed failed -> {
          appendPendingSuccess(entries, pendingSuccessfulStep);
          yield BookWorkflowStepExecutionState.terminal(
              failedResultWithRollback(planId, planStartedAt, entries, failed));
        }
      };
    } catch (RuntimeException exception) {
      appendPendingSuccess(entries, pendingSuccessfulStep);
      return BookWorkflowStepExecutionState.terminal(
          unexpectedStepFailure(planId, planStartedAt, entries, step, stepStartedAt, exception));
    }
  }

  private BookWorkflowExecutionResult unexpectedStepFailure(
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
    return result(planId, BookWorkflowExecutionStatus.REJECTED, planStartedAt, entries);
  }

  private BookWorkflowExecutionResult commitSuccessfulPlan(
      BookWorkflowPlanId planId,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep) {
    Instant commitStartedAt = Instant.now(clock);
    try {
      transactionStore.commitLedgerPlanTransaction();
    } catch (RuntimeException exception) {
      return boundaryFailureAfterRollback(
          BookWorkflowBoundaryFailureContext.afterJournalEntry(
              planId,
              startedAt,
              entries,
              BookWorkflowBoundaryCheckpoint.COMMIT,
              pendingSuccessfulStep,
              commitStartedAt),
          exception,
          null);
    }
    entries.add(pendingSuccessfulStep);
    return result(planId, BookWorkflowExecutionStatus.SUCCEEDED, startedAt, entries);
  }

  private static void appendPendingSuccess(
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.@Nullable Succeeded pendingSuccessfulStep) {
    if (pendingSuccessfulStep != null) {
      entries.add(pendingSuccessfulStep);
    }
  }

  private BookWorkflowExecutionResult failedResultWithRollback(
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
    return result(planId, failedStatus(failed), startedAt, entries);
  }

  private BookWorkflowExecutionResult boundaryFailureAfterRollback(
      BookWorkflowBoundaryFailureContext context,
      RuntimeException exception,
      @Nullable BookWorkflowFailure priorFailure) {
    RuntimeException rollbackFailure = rollbackFailure();
    return boundaryFailureResult(context, exception, rollbackFailure, priorFailure);
  }

  private BookWorkflowExecutionResult boundaryFailureResult(
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
