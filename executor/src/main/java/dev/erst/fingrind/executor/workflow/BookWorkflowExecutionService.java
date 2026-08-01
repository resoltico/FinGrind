package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Executes local workflow plans against one atomic book session. */
public final class BookWorkflowExecutionService {
  private final LedgerPlanExecutionStore transactionStore;
  private final Clock clock;
  private final LedgerPlanStepExecutor stepExecutor;
  private final BookWorkflowExecutionResultFactory resultFactory;
  private final BookWorkflowFailureRecovery failureRecovery;
  private final dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore
      attestationCommitmentStore;

  /** Creates one local workflow execution service. */
  public BookWorkflowExecutionService(
      LedgerPlanExecutionStore transactionStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this.transactionStore = Objects.requireNonNull(transactionStore, "transactionStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    resultFactory = new BookWorkflowExecutionResultFactory(this.clock);
    failureRecovery =
        new BookWorkflowFailureRecovery(
            this.clock, resultFactory, this.transactionStore::rollbackLedgerPlanTransaction);
    attestationCommitmentStore = this.transactionStore;
    this.stepExecutor =
        new LedgerPlanStepExecutor(
            this.transactionStore,
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            this.clock);
  }

  /** Executes one authorized local workflow plan atomically. */
  public BookWorkflowExecutionResult execute(
      BookWorkflowPlan plan, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(plan, "plan");
    try {
      AttestationPlanOperationAuthorizer planAuthorizer =
          new AttestationPlanOperationAuthorizer(
              AttestationOperationAuthorizer.require(attestationAuthorizer));
      Instant startedAt = Instant.now(clock);
      List<BookWorkflowJournalEntry> entries = new ArrayList<>();
      List<BookWorkflowStep> steps = plan.steps();
      BookWorkflowStep firstStep = steps.getFirst();

      BookWorkflowExecutionResult transactionFailure =
          beginTransactionOrReject(plan, startedAt, entries, planAuthorizer);
      if (transactionFailure != null) {
        return transactionFailure;
      }

      BookWorkflowExecutionResult initializationFailure =
          verifyWorkflowInitialization(plan, startedAt, firstStep, entries);
      if (initializationFailure != null) {
        return initializationFailure;
      }

      BookWorkflowStepExecutionState stepExecutionState =
          executeSteps(plan, startedAt, steps, entries, planAuthorizer);
      if (stepExecutionState.terminalResult() != null) {
        return Objects.requireNonNull(stepExecutionState.terminalResult(), "terminalResult");
      }
      return commitSuccessfulPlan(
          plan.planId(),
          startedAt,
          entries,
          planAuthorizer,
          Objects.requireNonNull(
              stepExecutionState.pendingSuccessfulStep(), "pendingSuccessfulStep"));
    } catch (AttestationStaleHeadException exception) {
      failureRecovery.preserveFailureAfterRollback(exception);
      throw exception;
    } catch (AttestationAdmissionRejectedException exception) {
      failureRecovery.preserveFailureAfterRollback(exception);
      throw exception;
    } catch (ContractFailureException exception) {
      failureRecovery.preserveFailureAfterRollback(exception);
      throw exception;
    }
  }

  private @Nullable BookWorkflowExecutionResult beginTransactionOrReject(
      BookWorkflowPlan plan,
      Instant startedAt,
      List<BookWorkflowJournalEntry> entries,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    try {
      transactionStore.beginLedgerPlanTransaction(plan.planId().value(), attestationAuthorizer);
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
      List<BookWorkflowJournalEntry> entries,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep = null;
    for (int stepOrder = 0; stepOrder < steps.size(); stepOrder++) {
      BookWorkflowStep step = steps.get(stepOrder);
      var stepOutcome =
          executeStep(
              plan.planId(),
              startedAt,
              entries,
              pendingSuccessfulStep,
              stepOrder,
              step,
              attestationAuthorizer);
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
      BookWorkflowStep step,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Instant stepStartedAt = Instant.now(clock);
    try {
      transactionStore.enterLedgerPlanStep(stepOrder);
      BookWorkflowJournalEntry stepEntry = stepExecutor.execute(step, attestationAuthorizer);
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
    } catch (AttestationStaleHeadException exception) {
      throw exception;
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
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
      AttestationPlanOperationAuthorizer attestationAuthorizer,
      BookWorkflowJournalEntry.Succeeded pendingSuccessfulStep) {
    Instant commitStartedAt = Instant.now(clock);
    @Nullable AttestationCommit attestationCommit = null;
    try {
      List<BookWorkflowJournalEntry> completedEntries = new ArrayList<>(entries);
      completedEntries.add(pendingSuccessfulStep);
      if (transactionStore.hasCompletedLedgerPlanChildren()) {
        attestationCommit =
            Objects.requireNonNull(
                transactionStore.appendPlanAttestation(commitStartedAt, attestationAuthorizer),
                "A mutating ledger plan must append aggregate attestation evidence.");
        completedEntries =
            BookWorkflowJournalAttestationHydrator.hydrate(
                completedEntries, attestationCommitmentStore, attestationCommit);
      }
      transactionStore.commitLedgerPlanTransaction();
      return resultFactory.result(
          planId,
          BookWorkflowExecutionStatus.SUCCEEDED,
          startedAt,
          completedEntries,
          attestationCommit);
    } catch (AttestationStaleHeadException exception) {
      throw exception;
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
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
