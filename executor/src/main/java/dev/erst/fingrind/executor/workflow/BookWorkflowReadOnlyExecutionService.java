package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes credential-free workflow plans through a physically read-only book capability. */
public final class BookWorkflowReadOnlyExecutionService {
  private final LedgerPlanReadOnlyExecutionStore transactionStore;
  private final Clock clock;
  private final LedgerPlanReadOnlyStepExecutor stepExecutor;
  private final BookWorkflowExecutionResultFactory resultFactory;
  private final BookWorkflowFailureRecovery failureRecovery;

  /** Creates one credential-free, read-only workflow execution service. */
  public BookWorkflowReadOnlyExecutionService(
      LedgerPlanReadOnlyExecutionStore transactionStore, Clock clock) {
    this.transactionStore = Objects.requireNonNull(transactionStore, "transactionStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    resultFactory = new BookWorkflowExecutionResultFactory(this.clock);
    failureRecovery =
        new BookWorkflowFailureRecovery(
            this.clock, resultFactory, this.transactionStore::rollbackLedgerPlanTransaction);
    stepExecutor = new LedgerPlanReadOnlyStepExecutor(this.transactionStore, this.clock);
  }

  /** Executes one credential-free workflow plan atomically against a stable read snapshot. */
  public BookWorkflowExecutionResult execute(BookWorkflowPlan plan) {
    Objects.requireNonNull(plan, "plan");
    try {
      Instant startedAt = Instant.now(clock);
      if (plan.containsBookMutation()) {
        return rejectedMutationPlan(plan, startedAt);
      }
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
    } catch (ContractFailureException exception) {
      failureRecovery.preserveFailureAfterRollback(exception);
      throw exception;
    }
  }

  /** Returns the deterministic admission rejection for a plan that requests a book mutation. */
  public BookWorkflowExecutionResult rejectMutationPlan(BookWorkflowPlan plan) {
    Objects.requireNonNull(plan, "plan");
    if (!plan.containsBookMutation()) {
      throw new IllegalArgumentException(
          "Only a plan containing a book mutation may be rejected here.");
    }
    return rejectedMutationPlan(plan, Instant.now(clock));
  }

  private BookWorkflowExecutionResult rejectedMutationPlan(
      BookWorkflowPlan plan, Instant startedAt) {
    BookWorkflowStep mutationStep =
        plan.firstBookMutationStep()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "A mutation-rejected plan must contain a mutation step."));
    return resultFactory.result(
        plan.planId(),
        BookWorkflowExecutionStatus.REJECTED,
        startedAt,
        List.of(stepExecutor.mutationForbiddenEntry(mutationStep, startedAt)));
  }

  private @Nullable BookWorkflowExecutionResult beginTransactionOrReject(
      BookWorkflowPlan plan, Instant startedAt, List<BookWorkflowJournalEntry> entries) {
    try {
      transactionStore.beginReadOnlyLedgerPlanTransaction(plan.planId().value());
      return null;
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return failureRecovery.boundaryFailureResult(
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
    try {
      if (stepExecutor.allowsInitializedWorkflow()) {
        return null;
      }
      return failureRecovery.failedResultWithRollback(
          plan.planId(), startedAt, entries, stepExecutor.missingBookEntry(firstStep, startedAt));
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return failureRecovery.boundaryFailureAfterRollback(
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
    for (int stepOrder = 0; stepOrder < steps.size(); stepOrder++) {
      BookWorkflowStep step = steps.get(stepOrder);
      BookWorkflowStepExecutionState stepOutcome =
          executeStep(plan.planId(), startedAt, entries, pendingSuccessfulStep, stepOrder, step);
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
      int stepOrder,
      BookWorkflowStep step) {
    Instant stepStartedAt = Instant.now(clock);
    try {
      transactionStore.enterLedgerPlanStep(stepOrder);
      BookWorkflowJournalEntry stepEntry = stepExecutor.execute(step);
      return switch (stepEntry) {
        case BookWorkflowJournalEntry.Succeeded succeeded -> {
          failureRecovery.appendPendingSuccess(entries, pendingSuccessfulStep);
          yield BookWorkflowStepExecutionState.pending(succeeded);
        }
        case BookWorkflowJournalEntry.Failed failed -> {
          failureRecovery.appendPendingSuccess(entries, pendingSuccessfulStep);
          yield BookWorkflowStepExecutionState.terminal(
              failureRecovery.failedResultWithRollback(planId, planStartedAt, entries, failed));
        }
      };
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      failureRecovery.appendPendingSuccess(entries, pendingSuccessfulStep);
      return BookWorkflowStepExecutionState.terminal(
          failureRecovery.unexpectedStepFailure(
              planId, planStartedAt, entries, step, stepStartedAt, exception));
    }
  }

  private BookWorkflowExecutionResult commitSuccessfulPlan(
      BookWorkflowPlanId planId,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep) {
    Instant commitStartedAt = Instant.now(clock);
    try {
      transactionStore.commitLedgerPlanTransaction();
      List<BookWorkflowJournalEntry> completedEntries = new ArrayList<>(entries);
      completedEntries.add(pendingSuccessfulStep);
      return resultFactory.result(
          planId, BookWorkflowExecutionStatus.SUCCEEDED, startedAt, completedEntries);
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      return failureRecovery.boundaryFailureAfterRollback(
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
  }
}
