package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.policy.BookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.policy.CoreBookkeepingPolicyPack;
import dev.erst.fingrind.executor.bookkeeping.posting.BookkeepingPostingService;
import dev.erst.fingrind.executor.bookkeeping.posting.PostingPreflightOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Executes one ledger-plan step and maps it into the canonical journal entry model. */
final class LedgerPlanStepExecutor {
  private final Clock clock;
  private final BookAdministrationService bookAdministrationService;
  private final BookkeepingReadService bookkeepingReadService;
  private final BookkeepingPostingService bookkeepingPostingService;

  LedgerPlanStepExecutor(
      BookAdministrationStore administrationStore,
      AccountCatalogStore accountCatalogStore,
      BookkeepingReadStore readStore,
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock) {
    this(
        administrationStore,
        accountCatalogStore,
        readStore,
        validationStore,
        commitStore,
        postingIdGenerator,
        clock,
        CoreBookkeepingPolicyPack.current());
  }

  LedgerPlanStepExecutor(
      BookAdministrationStore administrationStore,
      AccountCatalogStore accountCatalogStore,
      BookkeepingReadStore readStore,
      PostingValidationStore validationStore,
      PostingCommitStore commitStore,
      PostingIdGenerator postingIdGenerator,
      Clock clock,
      BookkeepingPolicyPack policyPack) {
    Objects.requireNonNull(administrationStore, "administrationStore");
    Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    Objects.requireNonNull(readStore, "readStore");
    Objects.requireNonNull(validationStore, "validationStore");
    Objects.requireNonNull(commitStore, "commitStore");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
    BookkeepingPolicyPack requiredPolicyPack = BookkeepingPolicyPack.requirePolicyPack(policyPack);
    this.bookAdministrationService =
        new BookAdministrationService(readStore, administrationStore, accountCatalogStore, clock);
    this.bookkeepingReadService = new BookkeepingReadService(readStore, requiredPolicyPack);
    this.bookkeepingPostingService =
        new BookkeepingPostingService(
            validationStore, commitStore, postingIdGenerator, clock, requiredPolicyPack);
  }

  BookLifecycleInspection inspectBook() {
    return bookkeepingReadService.inspectBook();
  }

  BookWorkflowJournalEntry execute(BookWorkflowStep step) {
    Instant startedAt = Instant.now(clock);
    LedgerPlanStepOutcome outcome =
        switch (step) {
          case BookWorkflowStep.OpenBook openBook -> openBookOutcome(openBook.bookIdentity());
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
            "The selected book is not initialized and the plan does not begin with an open book step.",
            List.of()));
  }

  private LedgerPlanStepOutcome openBookOutcome(dev.erst.fingrind.core.BookIdentity bookIdentity) {
    return switch (bookAdministrationService.openBook(bookIdentity)) {
      case BookOpeningOutcome.Opened opened ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              BookWorkflowFact.text("initializedAt", opened.initializedAt().toString()),
              BookWorkflowFact.text("entityName", opened.bookIdentity().entityName().value()),
              BookWorkflowFact.text(
                  "functionalCurrency", opened.bookIdentity().functionalCurrency().code()),
              BookWorkflowFact.text(
                  "fiscalYearStart", opened.bookIdentity().fiscalYearStart().wireValue()));
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
    return switch (bookkeepingPostingService.preflight(step.command())) {
      case PostingPreflightOutcome.Accepted accepted ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              BookWorkflowFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              BookWorkflowFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case PostingPreflightOutcome.Rejected rejected ->
          LedgerPlanOutcomeMapper.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome postEntryOutcome(BookWorkflowStep.PostEntry step) {
    return switch (bookkeepingPostingService.commit(step.command())) {
      case PostingCommitResult.Committed committed ->
          LedgerPlanOutcomeMapper.stepSucceeded(
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
                  "recordedAt", committed.postingFact().provenance().recordedAt().toString()));
      case PostingCommitResult.Rejected rejected ->
          LedgerPlanOutcomeMapper.postingRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome inspectBookOutcome() {
    BookLifecycleInspection inspection = inspectBook();
    BookLifecycleInspection.Status status = inspection.status();
    return LedgerPlanOutcomeMapper.stepSucceeded(
        BookWorkflowFact.text("state", status.wireValue()),
        BookWorkflowFact.flag("initialized", status.initialized()),
        BookWorkflowFact.flag("compatibleWithCurrentBinary", status.compatibleWithCurrentBinary()));
  }

  private LedgerPlanStepOutcome listAccountsOutcome(BookWorkflowStep.ListAccounts step) {
    return switch (bookkeepingReadService.listAccounts(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountRegistryPage> reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanFactMapper.accountPageFacts(reported.value()));
      case BookkeepingReadOutcome.Rejected<AccountRegistryPage> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome getPostingOutcome(BookWorkflowStep.GetPosting step) {
    return switch (bookkeepingReadService.getPosting(step.postingId())) {
      case BookkeepingReadOutcome.Reported<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanOutcomeMapper.postingFacts(reported.value())
                  .toArray(BookWorkflowFact[]::new));
      case BookkeepingReadOutcome.Rejected<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
              rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome listPostingsOutcome(BookWorkflowStep.ListPostings step) {
    return switch (bookkeepingReadService.listPostings(step.query())) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          LedgerPlanOutcomeMapper.stepSucceeded(
              LedgerPlanFactMapper.postingPageFacts(reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome accountBalanceOutcome(BookWorkflowStep.AccountBalance step) {
    return switch (bookkeepingReadService.accountBalance(step.query())) {
      case BookkeepingReadOutcome.Reported<AccountBalanceView> reported ->
          LedgerPlanOutcomeMapper.balanceFacts(reported.value());
      case BookkeepingReadOutcome.Rejected<AccountBalanceView> rejected ->
          LedgerPlanOutcomeMapper.queryRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome assertionOutcome(BookWorkflowAssertion assertion) {
    return LedgerPlanAssertionEvaluator.evaluate(bookkeepingReadService, assertion);
  }
}
