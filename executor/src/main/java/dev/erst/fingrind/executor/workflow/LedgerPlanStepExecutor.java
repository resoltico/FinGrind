package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Executes one ledger-plan step and maps it into the canonical journal entry model. */
final class LedgerPlanStepExecutor {
  private final Clock clock;
  private final BookkeepingReadService bookkeepingReadService;
  private final PostingApplicationService postingApplicationService;

  LedgerPlanStepExecutor(
      BookAdministrationStore administrationStore,
      AccountCatalogStore accountCatalogStore,
      BookkeepingReadStore readStore,
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      TaxAdministrationStore taxAdministrationStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    Objects.requireNonNull(administrationStore, "administrationStore");
    Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    Objects.requireNonNull(readStore, "readStore");
    Objects.requireNonNull(validationStore, "validationStore");
    Objects.requireNonNull(commitStore, "commitStore");
    Objects.requireNonNull(taxAdministrationStore, "taxAdministrationStore");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.bookkeepingReadService = new BookkeepingReadService(readStore);
    this.postingApplicationService =
        new PostingApplicationService(validationStore, commitStore, postingIdGenerator, clock);
  }

  BookLifecycleInspection inspectBook() {
    return bookkeepingReadService.inspectBook();
  }

  boolean allowsInitializedWorkflow() {
    return bookkeepingReadService.allowsInitializedWorkflow();
  }

  BookWorkflowJournalEntry execute(BookWorkflowStep step) {
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome =
        switch (step) {
          case BookWorkflowStep.EnsureBook ensureBook ->
              attestationRequired(ensureBook.stepId().value());
          case BookWorkflowStep.DeclareAccount declareAccount ->
              attestationRequired(declareAccount.stepId().value());
          case BookWorkflowStep.DeclareTaxRegistration declareTaxRegistration ->
              attestationRequired(declareTaxRegistration.stepId().value());
          case BookWorkflowStep.PreflightEntry preflightEntry -> preflightOutcome(preflightEntry);
          case BookWorkflowStep.PostEntry postEntry ->
              attestationRequired(postEntry.stepId().value());
          case BookWorkflowStep.InspectBook _ -> inspectBookOutcome();
          case BookWorkflowStep.ListAccounts listAccounts -> listAccountsOutcome(listAccounts);
          case BookWorkflowStep.GetPosting getPosting -> getPostingOutcome(getPosting);
          case BookWorkflowStep.ListPostings listPostings -> listPostingsOutcome(listPostings);
          case BookWorkflowStep.AccountBalance accountBalance ->
              accountBalanceOutcome(accountBalance);
          case BookWorkflowAssertionStep assertion -> assertionOutcome(assertion.assertion());
        };
    Instant finishedAt = Instant.now(clock);
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
            "The selected book is not initialized and the plan does not begin with an ensure-book step.",
            List.of()));
  }

  private static LedgerPlanStepOutcome attestationRequired(String stepId) {
    return new LedgerPlanStepOutcome.Rejected(
        new BookWorkflowFailure(
            "attestation-required",
            "Ledger-plan mutation requires the signed "
                + OperationId.EXECUTE_PLAN.wireName()
                + " path, which is not yet available for this step.",
            List.of(BookWorkflowFact.text("stepId", stepId))));
  }

  private LedgerPlanStepOutcome preflightOutcome(BookWorkflowStep.PreflightEntry step) {
    return switch (postingApplicationService.preflight(step.command())) {
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightAccepted accepted ->
          LedgerPlanStepOutcomes.stepSucceeded(
              BookWorkflowFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              BookWorkflowFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected rejected ->
          LedgerPlanRejectedOutcomes.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome inspectBookOutcome() {
    BookLifecycleInspection inspection = inspectBook();
    BookLifecycleInspection.Status status = inspection.status();
    return LedgerPlanStepOutcomes.stepSucceeded(
        BookWorkflowFact.text("state", status.wireValue()),
        BookWorkflowFact.flag("initialized", status.initialized()),
        BookWorkflowFact.flag("compatibleWithCurrentBinary", status.compatibleWithCurrentBinary()));
  }

  private LedgerPlanStepOutcome listAccountsOutcome(BookWorkflowStep.ListAccounts step) {
    return switch (bookkeepingReadService.listAccounts(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountPageFacts(reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome getPostingOutcome(BookWorkflowStep.GetPosting step) {
    return switch (bookkeepingReadService.getPosting(step.postingId())) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanStepOutcomes.postingFacts(reported.value())
                  .toArray(BookWorkflowFact[]::new));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome listPostingsOutcome(BookWorkflowStep.ListPostings step) {
    return switch (bookkeepingReadService.listPostings(step.query())) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.postingPageFacts(reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome accountBalanceOutcome(BookWorkflowStep.AccountBalance step) {
    return switch (bookkeepingReadService.accountBalance(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountBalanceView> reported ->
          LedgerPlanStepOutcomes.balanceFacts(reported.value());
      case BookkeepingReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanRejectedOutcomes.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome assertionOutcome(BookWorkflowAssertion assertion) {
    return LedgerPlanAssertionEvaluator.evaluate(bookkeepingReadService, assertion);
  }
}
