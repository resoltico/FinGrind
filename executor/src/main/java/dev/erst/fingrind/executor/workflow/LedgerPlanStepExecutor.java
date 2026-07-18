package dev.erst.fingrind.executor.workflow;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.executor.BookAdministrationService;
import dev.erst.fingrind.executor.PostingApplicationService;
import dev.erst.fingrind.executor.TaxAdministrationService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
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
  private final BookAdministrationService bookAdministrationService;
  private final BookkeepingReadService bookkeepingReadService;
  private final PostingApplicationService postingApplicationService;
  private final TaxAdministrationService taxAdministrationService;

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
    this.bookAdministrationService =
        new BookAdministrationService(readStore, administrationStore, accountCatalogStore, clock);
    this.bookkeepingReadService = new BookkeepingReadService(readStore);
    this.postingApplicationService =
        new PostingApplicationService(validationStore, commitStore, postingIdGenerator, clock);
    this.taxAdministrationService =
        new TaxAdministrationService(readStore, readStore, taxAdministrationStore, clock);
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
              ensureBookOutcome(ensureBook.bookIdentity());
          case BookWorkflowStep.DeclareAccount declareAccount ->
              declareAccountOutcome(declareAccount.command());
          case BookWorkflowStep.DeclareTaxRegistration declareTaxRegistration ->
              declareTaxRegistrationOutcome(declareTaxRegistration.command());
          case BookWorkflowStep.PreflightEntry preflightEntry -> preflightOutcome(preflightEntry);
          case BookWorkflowStep.PostEntry postEntry -> postEntryOutcome(postEntry);
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

  private LedgerPlanStepOutcome ensureBookOutcome(
      dev.erst.fingrind.core.BookIdentity bookIdentity) {
    BookLifecycleInspection inspection = inspectBook();
    if (inspection instanceof BookLifecycleInspection.Initialized initialized) {
      return initialized.bookIdentity().equals(bookIdentity)
          ? ensureBookSucceeded(initialized.initializedAt().toString(), initialized.bookIdentity())
          : LedgerPlanStepOutcomes.ensureBookIdentityConflict(
              initialized.bookIdentity(), bookIdentity);
    }
    return switch (bookAdministrationService.openBook(bookIdentity)) {
      case BookOpeningOutcome.Opened opened ->
          ensureBookSucceeded(opened.initializedAt().toString(), opened.bookIdentity());
      case BookOpeningOutcome.Rejected rejected ->
          reconcileEnsureBookRejection(bookIdentity, rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome ensureBookSucceeded(
      String initializedAt, dev.erst.fingrind.core.BookIdentity bookIdentity) {
    return LedgerPlanStepOutcomes.stepSucceeded(
        BookWorkflowFact.text("initializedAt", initializedAt),
        BookWorkflowFact.text("entityName", bookIdentity.entityName().value()),
        BookWorkflowFact.text("functionalCurrency", bookIdentity.functionalCurrency().code()),
        BookWorkflowFact.text("fiscalYearStart", bookIdentity.fiscalYearStart().wireValue()),
        BookWorkflowFact.text(
            "bookStartEffectiveDate", bookIdentity.bookStartEffectiveDate().toString()));
  }

  private LedgerPlanStepOutcome reconcileEnsureBookRejection(
      dev.erst.fingrind.core.BookIdentity requestedBookIdentity,
      dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection rejection) {
    if (!(rejection
        instanceof
        dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection
            .BookAlreadyInitialized)) {
      return LedgerPlanRejectedOutcomes.administrationRejection(rejection);
    }
    BookLifecycleInspection inspection = inspectBook();
    if (inspection instanceof BookLifecycleInspection.Initialized initialized) {
      return initialized.bookIdentity().equals(requestedBookIdentity)
          ? ensureBookSucceeded(initialized.initializedAt().toString(), initialized.bookIdentity())
          : LedgerPlanStepOutcomes.ensureBookIdentityConflict(
              initialized.bookIdentity(), requestedBookIdentity);
    }
    return LedgerPlanRejectedOutcomes.administrationRejection(rejection);
  }

  private LedgerPlanStepOutcome declareAccountOutcome(AccountDeclaration command) {
    return switch (bookAdministrationService.declareAccount(command)) {
      case AccountDeclarationOutcome.Declared declared ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("declared", declared.account()));
      case AccountDeclarationOutcome.Reactivated reactivated ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("reactivated", reactivated.account()));
      case AccountDeclarationOutcome.Renamed renamed ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("renamed", renamed.account()));
      case AccountDeclarationOutcome.Unchanged unchanged ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.accountDeclarationFacts("unchanged", unchanged.account()));
      case AccountDeclarationOutcome.Rejected rejected ->
          LedgerPlanRejectedOutcomes.administrationRejection(rejected.rejection());
    };
  }

  private LedgerPlanStepOutcome declareTaxRegistrationOutcome(
      dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand command) {
    return switch (taxAdministrationService.declareTaxRegistration(command)) {
      case DeclareTaxRegistrationResult.Declared declared ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("declared", declared.registration()));
      case DeclareTaxRegistrationResult.Updated updated ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("updated", updated.registration()));
      case DeclareTaxRegistrationResult.Unchanged unchanged ->
          LedgerPlanStepOutcomes.stepSucceeded(
              LedgerPlanFactMapper.taxRegistrationFacts("unchanged", unchanged.registration()));
      case DeclareTaxRegistrationResult.Rejected rejected ->
          LedgerPlanRejectedOutcomes.taxDeclarationRejection(rejected.rejection());
    };
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

  private LedgerPlanStepOutcome postEntryOutcome(BookWorkflowStep.PostEntry step) {
    return switch (postingApplicationService.commit(step.command())) {
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed committed ->
          LedgerPlanStepOutcomes.stepSucceeded(
              BookWorkflowFact.text("postingId", committed.postingId().value()),
              BookWorkflowFact.text("idempotencyKey", committed.idempotencyKey().value()),
              BookWorkflowFact.text("effectiveDate", committed.effectiveDate().toString()),
              BookWorkflowFact.text("recordedAt", committed.recordedAt().toString()));
      case dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected rejected ->
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
