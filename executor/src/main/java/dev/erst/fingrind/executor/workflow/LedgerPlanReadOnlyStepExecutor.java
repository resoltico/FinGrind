package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.LedgerPlanReadOnlyExecutionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Executes one credential-free, physically read-only ledger-plan step. */
final class LedgerPlanReadOnlyStepExecutor {
  private final Clock clock;
  private final LedgerPlanReadStepOutcomes readStepOutcomes;

  LedgerPlanReadOnlyStepExecutor(LedgerPlanReadOnlyExecutionStore executionStore, Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
    readStepOutcomes =
        new LedgerPlanReadStepOutcomes(
            Objects.requireNonNull(executionStore, "executionStore"), this.clock);
  }

  BookLifecycleInspection inspectBook() {
    return readStepOutcomes.inspectBook();
  }

  boolean allowsInitializedWorkflow() {
    return readStepOutcomes.allowsInitializedWorkflow();
  }

  BookWorkflowJournalEntry execute(BookWorkflowStep step) {
    Objects.requireNonNull(step, "step");
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome = readOnlyOutcome(step);
    return LedgerPlanStepExecutor.journalEntry(step, startedAt, Instant.now(clock), outcome);
  }

  BookWorkflowJournalEntry.Rejected mutationForbiddenEntry(
      BookWorkflowStep step, Instant startedAt) {
    return (BookWorkflowJournalEntry.Rejected)
        LedgerPlanStepExecutor.journalEntry(
            step, startedAt, Instant.now(clock), readOnlyMutationForbidden(step));
  }

  BookWorkflowJournalEntry.Rejected missingBookEntry(BookWorkflowStep step, Instant startedAt) {
    return new BookWorkflowJournalEntry.Rejected(
        step.stepId(),
        new BookWorkflowJournalDescriptor.Step(step),
        startedAt,
        Instant.now(clock),
        List.of(),
        new BookWorkflowFailure(
            LedgerPlanStepOutcomes.missingBookCode(step),
            "The selected book is not initialized. Create it with "
                + OperationId.OPEN_BOOK.wireName()
                + " before executing a plan.",
            List.of()));
  }

  private static LedgerPlanStepOutcome readOnlyMutationForbidden(BookWorkflowStep step) {
    return new LedgerPlanStepOutcome.Rejected(
        new BookWorkflowFailure(
            LedgerPlanFailure.READ_ONLY_PLAN_MUTATION_FORBIDDEN.code(),
            "The read-only ledger plan cannot execute mutating step '"
                + step.stepId().value()
                + "'.",
            List.of(BookWorkflowFact.text("stepId", step.stepId().value()))));
  }

  /**
   * Executes only step types with established read-only outcomes.
   *
   * <p>Every other step is rejected so a newly introduced workflow step cannot gain credential-free
   * execution before its read-only semantics are explicitly defined.
   */
  private LedgerPlanStepOutcome readOnlyOutcome(BookWorkflowStep step) {
    if (step instanceof BookWorkflowStep.PreflightEntry preflightEntry) {
      return readStepOutcomes.preflightOutcome(preflightEntry);
    }
    if (step instanceof BookWorkflowStep.InspectBook) {
      return readStepOutcomes.inspectBookOutcome();
    }
    if (step instanceof BookWorkflowStep.ListAccounts listAccounts) {
      return readStepOutcomes.listAccountsOutcome(listAccounts);
    }
    if (step instanceof BookWorkflowStep.GetPosting getPosting) {
      return readStepOutcomes.getPostingOutcome(getPosting);
    }
    if (step instanceof BookWorkflowStep.ListPostings listPostings) {
      return readStepOutcomes.listPostingsOutcome(listPostings);
    }
    if (step instanceof BookWorkflowStep.AccountBalance accountBalance) {
      return readStepOutcomes.accountBalanceOutcome(accountBalance);
    }
    if (step instanceof BookWorkflowAssertionStep assertion) {
      return readStepOutcomes.assertionOutcome(assertion.assertion());
    }
    return readOnlyMutationForbidden(step);
  }
}
