package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.workflow.BookWorkflowAssertion;
import dev.erst.fingrind.executor.workflow.BookWorkflowFailure;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalDescriptor;
import dev.erst.fingrind.executor.workflow.BookWorkflowJournalEntry;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Executes one ledger-plan step and maps it into the canonical journal entry model. */
final class LedgerPlanStepExecutor {
  private final Clock clock;
  private final BookAdministrationService bookAdministrationService;
  private final BookReadService bookReadService;
  private final PostingApplicationService postingApplicationService;

  LedgerPlanStepExecutor(
      LedgerPlanSession planSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    Objects.requireNonNull(planSession, "planSession");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.bookAdministrationService =
        new BookAdministrationService(planSession.administrationSession(), clock);
    this.bookReadService = new BookReadService(planSession.readSession());
    this.postingApplicationService =
        new PostingApplicationService(planSession.postingSession(), postingIdGenerator, clock);
  }

  boolean isInitialized() {
    return bookReadService.isInitialized();
  }

  BookWorkflowJournalEntry execute(BookWorkflowStep step) {
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome =
        switch (step) {
          case BookWorkflowStep.OpenBook _ -> openBookOutcome();
          case BookWorkflowStep.DeclareAccount declareAccount ->
              declareAccountOutcome(declareAccount.command());
          case BookWorkflowStep.PreflightEntry preflightEntry -> preflightOutcome(preflightEntry);
          case BookWorkflowStep.PostEntry postEntry -> postEntryOutcome(postEntry);
          case BookWorkflowStep.InspectBook _ -> inspectBookOutcome();
          case BookWorkflowStep.ListAccounts listAccounts -> listAccountsOutcome(listAccounts);
          case BookWorkflowStep.GetPosting getPosting -> getPostingOutcome(getPosting);
          case BookWorkflowStep.ListPostings listPostings -> listPostingsOutcome(listPostings);
          case BookWorkflowStep.AccountBalance accountBalance ->
              accountBalanceOutcome(accountBalance);
          case BookWorkflowStep.Assert assertion -> assertionOutcome(assertion.assertion());
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
            LedgerPlanOutcomeMapper.missingBookCode(step),
            "The selected book is not initialized and the plan does not begin with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".",
            List.of()));
  }

  private LedgerPlanStepOutcome openBookOutcome() {
    return switch (bookAdministrationService.openBook()) {
      case BookOpeningOutcome.Opened opened ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerFact.text("initializedAt", opened.initializedAt().toString()));
      case BookOpeningOutcome.Rejected rejected ->
          LedgerPlanOutcomeMapper.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome declareAccountOutcome(AccountDeclaration command) {
    return switch (bookAdministrationService.declareAccount(command)) {
      case AccountDeclarationOutcome.Declared declared ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanFactMapper.declaredAccountFacts(declared.account()));
      case AccountDeclarationOutcome.Rejected rejected ->
          LedgerPlanOutcomeMapper.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome preflightOutcome(BookWorkflowStep.PreflightEntry step) {
    return switch (postingApplicationService.preflight(step.command())) {
      case PostEntryResult.PreflightAccepted accepted ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case PostEntryResult.PreflightRejected rejected ->
          LedgerPlanOutcomeMapper.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome postEntryOutcome(BookWorkflowStep.PostEntry step) {
    return switch (postingApplicationService.commit(step.command())) {
      case PostEntryResult.Committed committed ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerFact.text("postingId", committed.postingId().value()),
              LedgerFact.text("idempotencyKey", committed.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", committed.effectiveDate().toString()),
              LedgerFact.text("recordedAt", committed.recordedAt().toString()));
      case PostEntryResult.CommitRejected rejected ->
          LedgerPlanOutcomeMapper.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome inspectBookOutcome() {
    BookInspection inspection = bookReadService.inspectBook();
    BookInspection.Status status = inspection.status();
    return LedgerPlanOutcomeMapper.stepSucceeded(
        LedgerFact.text("state", status.wireValue()),
        LedgerFact.flag("initialized", status.initialized()),
        LedgerFact.flag("compatibleWithCurrentBinary", status.compatibleWithCurrentBinary()));
  }

  private LedgerPlanStepOutcome listAccountsOutcome(BookWorkflowStep.ListAccounts step) {
    return switch (bookReadService.listAccountsOutcome(step.query())) {
      case BookReadOutcome.Reported<AccountRegistryPage> reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanFactMapper.accountPageFacts(reported.value()));
      case BookReadOutcome.Rejected<AccountRegistryPage> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome getPostingOutcome(BookWorkflowStep.GetPosting step) {
    return switch (bookReadService.getPostingOutcome(step.postingId())) {
      case BookReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanOutcomeMapper.postingFacts(reported.value()).toArray(LedgerFact[]::new));
      case BookReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome listPostingsOutcome(BookWorkflowStep.ListPostings step) {
    return switch (bookReadService.listPostingsOutcome(step.query())) {
      case BookReadOutcome.Reported<PostingHistoryPage> reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanFactMapper.postingPageFacts(reported.value()));
      case BookReadOutcome.Rejected<PostingHistoryPage> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome accountBalanceOutcome(BookWorkflowStep.AccountBalance step) {
    return switch (bookReadService.accountBalanceOutcome(step.query())) {
      case BookReadOutcome.Reported<AccountBalanceView> reported ->
          LedgerPlanOutcomeMapper.balanceFacts(reported.value());
      case BookReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome assertionOutcome(BookWorkflowAssertion assertion) {
    return LedgerPlanAssertionEvaluator.evaluate(bookReadService, assertion);
  }
}
