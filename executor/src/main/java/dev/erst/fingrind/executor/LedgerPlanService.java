package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.*;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Executes canonical AI-agent ledger plans against one atomic book session. */
public final class LedgerPlanService {
  private final LedgerPlanSession planSession;
  private final Clock clock;
  private final BookAdministrationService bookAdministrationService;
  private final BookQueryService bookQueryService;
  private final PostingApplicationService postingApplicationService;

  /** Creates a ledger-plan executor. */
  public LedgerPlanService(
      LedgerPlanSession bookSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.planSession = Objects.requireNonNull(bookSession, "bookSession");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.bookAdministrationService =
        new BookAdministrationService(bookSession.administrationSession(), clock);
    this.bookQueryService = new BookQueryService(bookSession.querySession());
    this.postingApplicationService =
        new PostingApplicationService(bookSession.postingSession(), postingIdGenerator, clock);
  }

  /** Executes one plan atomically, committing only when every step succeeds. */
  public LedgerPlanResult execute(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    Instant startedAt = Instant.now(clock);
    List<LedgerJournalEntry> entries = new ArrayList<>();
    planSession.beginLedgerPlanTransaction();
    boolean rollbackRequired = true;
    try {
      if (!plan.beginsWithOpenBook() && !bookQueryService.isInitialized()) {
        LedgerStep firstStep = plan.steps().getFirst();
        entries.add(missingBookEntry(firstStep, startedAt));
        planSession.rollbackLedgerPlanTransaction();
        rollbackRequired = false;
        return result(plan.planId(), LedgerPlanStatus.REJECTED, startedAt, entries);
      }
      for (LedgerStep step : plan.steps()) {
        LedgerJournalEntry entry = executeStep(step);
        entries.add(entry);
        if (entry.status() != LedgerStepStatus.SUCCEEDED) {
          planSession.rollbackLedgerPlanTransaction();
          rollbackRequired = false;
          return result(
              plan.planId(),
              entry.status() == LedgerStepStatus.REJECTED
                  ? LedgerPlanStatus.REJECTED
                  : LedgerPlanStatus.ASSERTION_FAILED,
              startedAt,
              entries);
        }
      }
      planSession.commitLedgerPlanTransaction();
      rollbackRequired = false;
      return result(plan.planId(), LedgerPlanStatus.SUCCEEDED, startedAt, entries);
    } finally {
      if (rollbackRequired) {
        planSession.rollbackLedgerPlanTransaction();
      }
    }
  }

  private LedgerJournalEntry executeStep(LedgerStep step) {
    Instant startedAt = Instant.now(clock);
    StepOutcome outcome =
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
      case StepSucceeded succeeded ->
          new LedgerJournalEntry.Succeeded(
              step.stepId(),
              step.kind(),
              step.detailKind(),
              startedAt,
              finishedAt,
              succeeded.facts());
      case StepRejected rejected ->
          new LedgerJournalEntry.Rejected(
              step.stepId(),
              step.kind(),
              step.detailKind(),
              startedAt,
              finishedAt,
              rejected.facts(),
              rejected.failure());
      case StepAssertionFailed assertionFailed ->
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

  private StepOutcome openBookOutcome() {
    return switch (bookAdministrationService.openBook()) {
      case OpenBookResult.Opened opened ->
          stepSucceeded(LedgerFact.text("initializedAt", opened.initializedAt().toString()));
      case OpenBookResult.Rejected rejected -> administrationRejection(rejected.rejection());
    };
  }

  private StepOutcome declareAccountOutcome(
      dev.erst.fingrind.contract.DeclareAccountCommand command) {
    return switch (bookAdministrationService.declareAccount(command)) {
      case DeclareAccountResult.Declared declared ->
          stepSucceeded(
              LedgerFact.text("accountCode", declared.account().accountCode().value()),
              LedgerFact.text("normalBalance", declared.account().normalBalance().wireValue()),
              LedgerFact.flag("active", declared.account().active()));
      case DeclareAccountResult.Rejected rejected -> administrationRejection(rejected.rejection());
    };
  }

  private StepOutcome preflightOutcome(LedgerStep.PreflightEntry step) {
    return switch (postingApplicationService.preflight(step.command())) {
      case PostEntryResult.PreflightAccepted accepted ->
          stepSucceeded(
              LedgerFact.text("idempotencyKey", accepted.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", accepted.effectiveDate().toString()));
      case PostEntryResult.PreflightRejected rejected -> postingRejection(rejected.rejection());
    };
  }

  private StepOutcome postEntryOutcome(LedgerStep.PostEntry step) {
    return switch (postingApplicationService.commit(step.command())) {
      case PostEntryResult.Committed committed ->
          stepSucceeded(
              LedgerFact.text("postingId", committed.postingId().value()),
              LedgerFact.text("idempotencyKey", committed.idempotencyKey().value()),
              LedgerFact.text("effectiveDate", committed.effectiveDate().toString()),
              LedgerFact.text("recordedAt", committed.recordedAt().toString()));
      case PostEntryResult.CommitRejected rejected -> postingRejection(rejected.rejection());
    };
  }

  private StepOutcome inspectBookOutcome() {
    BookInspection inspection = bookQueryService.inspectBook();
    return stepSucceeded(
        LedgerFact.text("state", inspection.status().wireValue()),
        LedgerFact.flag("initialized", inspection.initialized()),
        LedgerFact.flag("compatibleWithCurrentBinary", inspection.compatibleWithCurrentBinary()));
  }

  private StepOutcome listAccountsOutcome(LedgerStep.ListAccounts step) {
    return switch (bookQueryService.listAccounts(step.query())) {
      case ListAccountsResult.Listed listed ->
          stepSucceeded(
              LedgerFact.count("count", listed.page().accounts().size()),
              LedgerFact.flag("hasMore", listed.page().hasMore()));
      case ListAccountsResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome getPostingOutcome(LedgerStep.GetPosting step) {
    return switch (bookQueryService.getPosting(step.postingId())) {
      case GetPostingResult.Found found ->
          stepSucceeded(postingFacts(found.postingFact()).toArray(LedgerFact[]::new));
      case GetPostingResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome listPostingsOutcome(LedgerStep.ListPostings step) {
    return switch (bookQueryService.listPostings(step.query())) {
      case ListPostingsResult.Listed listed ->
          stepSucceeded(
              LedgerFact.count("count", listed.page().postings().size()),
              LedgerFact.flag("hasMore", listed.page().hasMore()));
      case ListPostingsResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome accountBalanceOutcome(LedgerStep.AccountBalance step) {
    return switch (bookQueryService.accountBalance(step.query())) {
      case AccountBalanceResult.Reported reported -> balanceFacts(reported.snapshot());
      case AccountBalanceResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome assertionOutcome(LedgerAssertion assertion) {
    return switch (assertion) {
      case LedgerAssertion.AccountDeclared accountDeclared ->
          assertAccountDeclared(accountDeclared);
      case LedgerAssertion.AccountActive accountActive -> assertAccountActive(accountActive);
      case LedgerAssertion.PostingExists postingExists -> assertPostingExists(postingExists);
      case LedgerAssertion.AccountBalanceEquals balanceEquals ->
          assertAccountBalance(balanceEquals);
    };
  }

  private StepOutcome assertAccountDeclared(LedgerAssertion.AccountDeclared assertion) {
    boolean present = bookQueryService.findAccount(assertion.accountCode()).isPresent();
    return present
        ? stepSucceeded(LedgerFact.text("accountCode", assertion.accountCode().value()))
        : assertionFailure(
            "Account is not declared.",
            LedgerFact.text("accountCode", assertion.accountCode().value()));
  }

  private StepOutcome assertAccountActive(LedgerAssertion.AccountActive assertion) {
    Optional<dev.erst.fingrind.contract.DeclaredAccount> account =
        bookQueryService.findAccount(assertion.accountCode());
    if (account.isEmpty()) {
      return assertionFailure(
          "Account is not declared.",
          LedgerFact.text("accountCode", assertion.accountCode().value()));
    }
    return account.orElseThrow().active()
        ? stepSucceeded(LedgerFact.text("accountCode", assertion.accountCode().value()))
        : assertionFailure(
            "Account is not active.",
            LedgerFact.text("accountCode", assertion.accountCode().value()));
  }

  private StepOutcome assertPostingExists(LedgerAssertion.PostingExists assertion) {
    boolean present = bookQueryService.findPosting(assertion.postingId()).isPresent();
    return present
        ? stepSucceeded(LedgerFact.text("postingId", assertion.postingId().value()))
        : assertionFailure(
            "Posting does not exist.", LedgerFact.text("postingId", assertion.postingId().value()));
  }

  private StepOutcome assertAccountBalance(LedgerAssertion.AccountBalanceEquals assertion) {
    return switch (bookQueryService.accountBalance(assertion.query())) {
      case AccountBalanceResult.Reported reported ->
          assertAccountBalance(assertion, reported.snapshot());
      case AccountBalanceResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private static StepOutcome assertAccountBalance(
      LedgerAssertion.AccountBalanceEquals assertion, AccountBalanceSnapshot snapshot) {
    Optional<CurrencyBalance> matchingBalance =
        snapshot.balances().stream()
            .filter(
                balance ->
                    balance.netAmount().currencyCode().equals(assertion.netAmount().currencyCode()))
            .findFirst();
    if (matchingBalance.isEmpty()) {
      return assertionFailure(
          "Expected currency balance bucket does not exist.",
          LedgerFact.text("accountCode", assertion.accountCode().value()),
          LedgerFact.text("currencyCode", assertion.netAmount().currencyCode().value()));
    }
    CurrencyBalance balance = matchingBalance.orElseThrow();
    boolean matchesAmount = balance.netAmount().equals(assertion.netAmount());
    boolean matchesSide = balance.balanceSide() == assertion.balanceSide();
    if (matchesAmount && matchesSide) {
      return stepSucceeded(balanceFacts(snapshot).facts().toArray(LedgerFact[]::new));
    }
    return assertionFailure(
        "Account balance does not match expected value.",
        LedgerFact.text("accountCode", assertion.accountCode().value()),
        LedgerFact.text("currencyCode", assertion.netAmount().currencyCode().value()),
        LedgerFact.text("expectedNetAmount", assertion.netAmount().amount().toPlainString()),
        LedgerFact.text("actualNetAmount", balance.netAmount().amount().toPlainString()),
        LedgerFact.text("expectedBalanceSide", assertion.balanceSide().wireValue()),
        LedgerFact.text("actualBalanceSide", balance.balanceSide().wireValue()));
  }

  private static StepOutcome balanceFacts(AccountBalanceSnapshot snapshot) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.text("accountCode", snapshot.account().accountCode().value()));
    facts.add(LedgerFact.count("bucketCount", snapshot.balances().size()));
    for (CurrencyBalance balance : snapshot.balances()) {
      facts.add(
          LedgerFact.group(
              "balance",
              List.of(
                  LedgerFact.text("currencyCode", balance.netAmount().currencyCode().value()),
                  LedgerFact.text("debitTotal", balance.debitTotal().amount().toPlainString()),
                  LedgerFact.text("creditTotal", balance.creditTotal().amount().toPlainString()),
                  LedgerFact.text("netAmount", balance.netAmount().amount().toPlainString()),
                  LedgerFact.text("balanceSide", balance.balanceSide().wireValue()))));
    }
    return stepSucceeded(facts.toArray(LedgerFact[]::new));
  }

  private static List<LedgerFact> postingFacts(PostingFact postingFact) {
    return List.of(
        LedgerFact.text("postingId", postingFact.postingId().value()),
        LedgerFact.text(
            "idempotencyKey",
            postingFact.provenance().requestProvenance().idempotencyKey().value()),
        LedgerFact.text("effectiveDate", postingFact.journalEntry().effectiveDate().toString()),
        LedgerFact.text("recordedAt", postingFact.provenance().recordedAt().toString()));
  }

  private static StepOutcome administrationRejection(BookAdministrationRejection rejection) {
    return stepRejected(
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  private static StepOutcome queryRejection(BookQueryRejection rejection) {
    return stepRejected(
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  private static StepOutcome postingRejection(PostingRejection rejection) {
    return stepRejected(
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        RejectionNarrative.facts(rejection));
  }

  private static StepOutcome assertionFailure(String message, LedgerFact... facts) {
    return stepAssertionFailed(
        new LedgerStepFailure(
            LedgerStepStatus.ASSERTION_FAILED.wireValue(), message, List.of(facts)));
  }

  private static String missingBookCode(LedgerStep firstStep) {
    return switch (firstStep.kind()) {
      case OPEN_BOOK, DECLARE_ACCOUNT ->
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookNotInitialized());
      case PREFLIGHT_ENTRY, POST_ENTRY ->
          PostingRejection.wireCode(new PostingRejection.BookNotInitialized());
      case INSPECT_BOOK, LIST_ACCOUNTS, GET_POSTING, LIST_POSTINGS, ACCOUNT_BALANCE, ASSERT ->
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized());
    };
  }

  private LedgerPlanResult result(
      LedgerPlanId planId,
      LedgerPlanStatus status,
      Instant startedAt,
      List<LedgerJournalEntry> entries) {
    LedgerExecutionJournal journal =
        new LedgerExecutionJournal(startedAt, Instant.now(clock), List.copyOf(entries));
    return switch (status) {
      case SUCCEEDED -> new LedgerPlanResult.Succeeded(planId, journal);
      case REJECTED -> new LedgerPlanResult.Rejected(planId, journal);
      case ASSERTION_FAILED -> new LedgerPlanResult.AssertionFailed(planId, journal);
    };
  }

  private LedgerJournalEntry.Rejected rejectedEntry(
      LedgerStep step, Instant startedAt, LedgerStepFailure failure) {
    return new LedgerJournalEntry.Rejected(
        step.stepId(),
        step.kind(),
        step.detailKind(),
        startedAt,
        Instant.now(clock),
        failure.facts(),
        failure);
  }

  private LedgerJournalEntry missingBookEntry(LedgerStep step, Instant startedAt) {
    return rejectedEntry(
        step,
        startedAt,
        new LedgerStepFailure(
            missingBookCode(step),
            "The selected book is not initialized and the plan does not begin with open-book.",
            List.of()));
  }

  private static StepOutcome stepSucceeded(LedgerFact... facts) {
    return new StepSucceeded(List.of(facts));
  }

  private static StepOutcome stepRejected(String code, String message, List<LedgerFact> facts) {
    return new StepRejected(new LedgerStepFailure(code, message, facts));
  }

  private static StepOutcome stepAssertionFailed(LedgerStepFailure failure) {
    return new StepAssertionFailed(failure);
  }

  /** Internal outcome model for one ledger-plan step execution. */
  private sealed interface StepOutcome permits StepSucceeded, StepRejected, StepAssertionFailed {
    /** Returns the fact list to record on the emitted journal step. */
    List<LedgerFact> facts();
  }

  /** Successful step outcome carrying facts and no failure payload. */
  private static final class StepSucceeded implements StepOutcome {
    private final List<LedgerFact> facts;

    private StepSucceeded(List<LedgerFact> facts) {
      this.facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
    }

    @Override
    public List<LedgerFact> facts() {
      return facts;
    }
  }

  /** Rejected step outcome carrying the failure that will be written to the journal. */
  private static final class StepRejected implements StepOutcome {
    private final LedgerStepFailure failure;

    private StepRejected(LedgerStepFailure failure) {
      this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<LedgerFact> facts() {
      return failure.facts();
    }

    private LedgerStepFailure failure() {
      return failure;
    }
  }

  /** Assertion-failed step outcome carrying the assertion failure payload. */
  private static final class StepAssertionFailed implements StepOutcome {
    private final LedgerStepFailure failure;

    private StepAssertionFailed(LedgerStepFailure failure) {
      this.failure = Objects.requireNonNull(failure, "failure");
    }

    @Override
    public List<LedgerFact> facts() {
      return failure.facts();
    }

    private LedgerStepFailure failure() {
      return failure;
    }
  }
}
