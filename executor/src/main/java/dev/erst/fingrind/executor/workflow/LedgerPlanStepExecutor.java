package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.PlanAccountDeclarationService;
import dev.erst.fingrind.executor.PlanPostEntryOutcome;
import dev.erst.fingrind.executor.PlanPostingApplicationService;
import dev.erst.fingrind.executor.PlanTaxRegistrationService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.LedgerPlanExecutionStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Executes one aggregate-attested ledger-plan step and maps it into the canonical journal. */
final class LedgerPlanStepExecutor {
  private final Clock clock;
  private final PlanAccountDeclarationService planAccountDeclarationService;
  private final PlanPostingApplicationService planPostingApplicationService;
  private final PlanTaxRegistrationService planTaxRegistrationService;
  private final LedgerPlanReadStepOutcomes readStepOutcomes;

  LedgerPlanStepExecutor(
      LedgerPlanExecutionStore executionStore, PostingIdGenerator postingIdGenerator, Clock clock) {
    LedgerPlanExecutionStore checkedExecutionStore =
        Objects.requireNonNull(executionStore, "executionStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    readStepOutcomes = new LedgerPlanReadStepOutcomes(checkedExecutionStore, this.clock);
    planAccountDeclarationService =
        new PlanAccountDeclarationService(
            checkedExecutionStore, checkedExecutionStore, checkedExecutionStore, this.clock);
    planPostingApplicationService =
        new PlanPostingApplicationService(
            checkedExecutionStore,
            checkedExecutionStore,
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"),
            this.clock);
    planTaxRegistrationService =
        new PlanTaxRegistrationService(
            checkedExecutionStore, checkedExecutionStore, checkedExecutionStore, this.clock);
  }

  BookLifecycleInspection inspectBook() {
    return readStepOutcomes.inspectBook();
  }

  boolean allowsInitializedWorkflow() {
    return readStepOutcomes.allowsInitializedWorkflow();
  }

  BookWorkflowJournalEntry execute(
      BookWorkflowStep step, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(step, "step");
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome =
        switch (step) {
          case BookWorkflowStep.DeclareAccount declareAccount ->
              declareAccountOutcome(declareAccount.command(), attestationAuthorizer);
          case BookWorkflowStep.DeclareTaxRegistration declareTaxRegistration ->
              declareTaxRegistrationOutcome(
                  declareTaxRegistration.command(), attestationAuthorizer);
          case BookWorkflowStep.PreflightEntry preflightEntry ->
              readStepOutcomes.preflightOutcome(preflightEntry);
          case BookWorkflowStep.PostEntry postEntry ->
              postEntryOutcome(postEntry, attestationAuthorizer);
          case BookWorkflowStep.InspectBook _ -> readStepOutcomes.inspectBookOutcome();
          case BookWorkflowStep.ListAccounts listAccounts ->
              readStepOutcomes.listAccountsOutcome(listAccounts);
          case BookWorkflowStep.GetPosting getPosting ->
              readStepOutcomes.getPostingOutcome(getPosting);
          case BookWorkflowStep.ListPostings listPostings ->
              readStepOutcomes.listPostingsOutcome(listPostings);
          case BookWorkflowStep.AccountBalance accountBalance ->
              readStepOutcomes.accountBalanceOutcome(accountBalance);
          case BookWorkflowAssertionStep assertion ->
              readStepOutcomes.assertionOutcome(assertion.assertion());
        };
    return journalEntry(step, startedAt, Instant.now(clock), outcome);
  }

  static BookWorkflowJournalEntry journalEntry(
      BookWorkflowStep step, Instant startedAt, Instant finishedAt, LedgerPlanStepOutcome outcome) {
    return switch (outcome) {
      case LedgerPlanStepOutcome.Succeeded succeeded ->
          new BookWorkflowJournalEntry.Succeeded(
              step.stepId(),
              new BookWorkflowJournalDescriptor.Step(step),
              startedAt,
              finishedAt,
              succeeded.facts());
      case LedgerPlanStepOutcome.Rejected rejected ->
          new BookWorkflowJournalEntry.Rejected(
              step.stepId(),
              new BookWorkflowJournalDescriptor.Step(step),
              startedAt,
              finishedAt,
              rejected.facts(),
              rejected.failure());
      case LedgerPlanStepOutcome.AssertionFailed assertionFailed ->
          new BookWorkflowJournalEntry.AssertionFailed(
              step.stepId(),
              new BookWorkflowJournalDescriptor.Step(step),
              startedAt,
              finishedAt,
              assertionFailed.facts(),
              assertionFailed.failure());
    };
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

  private LedgerPlanStepOutcome declareAccountOutcome(
      AccountDeclaration command, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    return switch (planAccountDeclarationService.declareAccount(command, attestationAuthorizer)) {
      case PlanAccountDeclarationOutcome.Declared declared ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("declared", declared.account()));
      case PlanAccountDeclarationOutcome.Reactivated reactivated ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("reactivated", reactivated.account()));
      case PlanAccountDeclarationOutcome.Renamed renamed ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("renamed", renamed.account()));
      case PlanAccountDeclarationOutcome.Unchanged unchanged ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("unchanged", unchanged.account()));
      case PlanAccountDeclarationOutcome.Rejected rejected ->
          LedgerPlanRejectedOutcomes.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome declareTaxRegistrationOutcome(
      dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand command,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    return switch (planTaxRegistrationService.declareTaxRegistration(
        command, attestationAuthorizer)) {
      case PlanTaxRegistrationMutationOutcome.Declared declared ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("declared", declared.registration()));
      case PlanTaxRegistrationMutationOutcome.Updated updated ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("updated", updated.registration()));
      case PlanTaxRegistrationMutationOutcome.Unchanged unchanged ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("unchanged", unchanged.registration()));
      case PlanTaxRegistrationMutationOutcome.Rejected rejected ->
          LedgerPlanRejectedOutcomes.taxDeclarationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome postEntryOutcome(
      BookWorkflowStep.PostEntry step, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    return switch (planPostingApplicationService.commit(step.command(), attestationAuthorizer)) {
      case PlanPostEntryOutcome.Committed committed ->
          LedgerPlanStepOutcomes.stepSucceeded(
              BookWorkflowFact.text("postingId", committed.postingFact().postingId().value()),
              BookWorkflowFact.text(
                  "idempotencyKey",
                  committed
                      .postingFact()
                      .provenance()
                      .requestProvenance()
                      .idempotencyKey()
                      .value()),
              BookWorkflowFact.text(
                  "effectiveDate",
                  committed.postingFact().journalEntry().effectiveDate().toString()),
              BookWorkflowFact.text(
                  "recordedAt", committed.postingFact().provenance().recordedAt().toString()),
              BookWorkflowFact.flag("idempotentReplay", committed.idempotentReplay()));
      case PlanPostEntryOutcome.Rejected rejected ->
          LedgerPlanRejectedOutcomes.postingRejection(rejected.rejection());
    };
  }
}
