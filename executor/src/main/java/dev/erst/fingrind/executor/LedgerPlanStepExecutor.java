package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
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

  LedgerJournalEntry execute(LedgerStep step) {
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome =
        switch (step) {
          case LedgerStep.OpenBook _ -> openBookOutcome();
          case LedgerStep.DeclareAccount declareAccount ->
              declareAccountOutcome(declareAccount.command());
          case LedgerStep.PreflightEntry preflightEntry -> preflightOutcome(preflightEntry);
          case LedgerStep.PostEntry postEntry -> postEntryOutcome(postEntry);
          case LedgerStep.InspectBook _ -> inspectBookOutcome();
          case LedgerStep.ListAccounts listAccounts -> listAccountsOutcome(listAccounts);
          case LedgerStep.GetPosting getPosting -> getPostingOutcome(getPosting);
          case LedgerStep.ListPostings listPostings -> listPostingsOutcome(listPostings);
          case LedgerStep.AccountBalance accountBalance -> accountBalanceOutcome(accountBalance);
          case LedgerStep.Assert assertion -> assertionOutcome(assertion.assertion());
        };
    Instant finishedAt = Instant.now(clock);
    return switch (outcome) {
      case LedgerPlanStepOutcome.Succeeded succeeded ->
          new LedgerJournalEntry.Succeeded(
              step.stepId(),
              step.kind(),
              step.detailKind(),
              startedAt,
              finishedAt,
              succeeded.facts());
      case LedgerPlanStepOutcome.Rejected rejected ->
          new LedgerJournalEntry.Rejected(
              step.stepId(),
              step.kind(),
              step.detailKind(),
              startedAt,
              finishedAt,
              rejected.facts(),
              rejected.failure());
      case LedgerPlanStepOutcome.AssertionFailed assertionFailed ->
          new LedgerJournalEntry.AssertionFailed(
              step.stepId(),
              step.kind(),
              step.detailKind(),
              startedAt,
              finishedAt,
              assertionFailed.facts(),
              assertionFailed.failure());
    };
  }

  LedgerJournalEntry.Rejected missingBookEntry(LedgerStep step, Instant startedAt) {
    return new LedgerJournalEntry.Rejected(
        step.stepId(),
        step.kind(),
        step.detailKind(),
        startedAt,
        Instant.now(clock),
        List.of(),
        new LedgerStepFailure(
            LedgerPlanOutcomeSupport.missingBookCode(step),
            "The selected book is not initialized and the plan does not begin with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".",
            List.of()));
  }

  private LedgerPlanStepOutcome openBookOutcome() {
    return switch (bookAdministrationService.openBook()) {
      case OpenBookResult.Opened opened ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.text("initializedAt", opened.initializedAt().toString()));
      case OpenBookResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome declareAccountOutcome(
      dev.erst.fingrind.contract.DeclareAccountCommand command) {
    return switch (bookAdministrationService.declareAccount(command)) {
      case DeclareAccountResult.Declared declared ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.text("accountCode", declared.account().accountCode().value()),
              LedgerFact.text("normalBalance", declared.account().normalBalance().wireValue()),
              LedgerFact.flag("active", declared.account().active()));
      case DeclareAccountResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome preflightOutcome(LedgerStep.PreflightEntry step) {
    return switch (postingApplicationService.preflight(step.command())) {
      case PostEntryResult.PreflightAccepted accepted ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case PostEntryResult.PreflightRejected rejected ->
          LedgerPlanOutcomeSupport.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome postEntryOutcome(LedgerStep.PostEntry step) {
    return switch (postingApplicationService.commit(step.command())) {
      case PostEntryResult.Committed committed ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.text("postingId", committed.postingId().value()),
              LedgerFact.text("idempotencyKey", committed.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", committed.effectiveDate().toString()),
              LedgerFact.text("recordedAt", committed.recordedAt().toString()));
      case PostEntryResult.CommitRejected rejected ->
          LedgerPlanOutcomeSupport.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome inspectBookOutcome() {
    BookInspection inspection = bookReadService.inspectBook();
    return LedgerPlanOutcomeSupport.stepSucceeded(
        LedgerFact.text("state", inspection.status().wireValue()),
        LedgerFact.flag("initialized", inspection.initialized()),
        LedgerFact.flag("compatibleWithCurrentBinary", inspection.compatibleWithCurrentBinary()));
  }

  private LedgerPlanStepOutcome listAccountsOutcome(LedgerStep.ListAccounts step) {
    return switch (bookReadService.listAccounts(step.query())) {
      case ListAccountsResult.Listed listed ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.count("count", listed.page().accounts().size()),
              LedgerFact.flag("hasMore", listed.page().hasMore()));
      case ListAccountsResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome getPostingOutcome(LedgerStep.GetPosting step) {
    return switch (bookReadService.getPosting(step.postingId())) {
      case dev.erst.fingrind.contract.GetPostingResult.Found found ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerPlanOutcomeSupport.postingFacts(found.postingFact())
                  .toArray(LedgerFact[]::new));
      case dev.erst.fingrind.contract.GetPostingResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome listPostingsOutcome(LedgerStep.ListPostings step) {
    return switch (bookReadService.listPostings(step.query())) {
      case ListPostingsResult.Listed listed ->
          LedgerPlanOutcomeSupport.stepSucceeded(
              LedgerFact.count("count", listed.page().postings().size()),
              LedgerFact.flag("hasMore", listed.page().hasMore()));
      case ListPostingsResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome accountBalanceOutcome(LedgerStep.AccountBalance step) {
    return switch (bookReadService.accountBalance(step.query())) {
      case AccountBalanceResult.Reported reported ->
          LedgerPlanOutcomeSupport.balanceFacts(reported.snapshot());
      case AccountBalanceResult.Rejected rejected ->
          LedgerPlanOutcomeSupport.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome assertionOutcome(LedgerAssertion assertion) {
    return LedgerPlanAssertionSupport.evaluate(bookReadService, assertion);
  }
}
