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
  private static final String ASSERTION_FAILED = "assertion-failed";

  private final LedgerPlanSession bookSession;
  private final PostingIdGenerator postingIdGenerator;
  private final Clock clock;

  /** Creates a ledger-plan executor. */
  public LedgerPlanService(
      LedgerPlanSession bookSession, PostingIdGenerator postingIdGenerator, Clock clock) {
    this.bookSession = Objects.requireNonNull(bookSession, "bookSession");
    this.postingIdGenerator = Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Executes one plan atomically, committing only when every step succeeds. */
  public LedgerPlanResult execute(LedgerPlan plan) {
    Objects.requireNonNull(plan, "plan");
    Instant startedAt = Instant.now(clock);
    List<LedgerJournalEntry> entries = new ArrayList<>();
    if (!(plan.steps().getFirst() instanceof LedgerStep.OpenBook) && !bookSession.isInitialized()) {
      LedgerStep firstStep = plan.steps().getFirst();
      entries.add(
          rejectedEntry(
              firstStep,
              startedAt,
              new LedgerStepFailure(
                  "book-not-initialized",
                  "The selected book is not initialized and the plan does not begin with open-book.",
                  List.of())));
      return result(plan.planId(), LedgerPlanStatus.REJECTED, startedAt, entries);
    }
    bookSession.beginLedgerPlanTransaction();
    boolean rollbackRequired = true;
    try {
      for (LedgerStep step : plan.steps()) {
        LedgerJournalEntry entry = executeStep(step);
        entries.add(entry);
        if (entry.status() != LedgerStepStatus.SUCCEEDED) {
          bookSession.rollbackLedgerPlanTransaction();
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
      bookSession.commitLedgerPlanTransaction();
      rollbackRequired = false;
      return result(plan.planId(), LedgerPlanStatus.SUCCEEDED, startedAt, entries);
    } finally {
      if (rollbackRequired) {
        bookSession.rollbackLedgerPlanTransaction();
      }
    }
  }

  private LedgerJournalEntry executeStep(LedgerStep step) {
    Instant startedAt = Instant.now(clock);
    StepOutcome outcome =
        switch (step) {
          case LedgerStep.OpenBook ignored -> openBookOutcome();
          case LedgerStep.DeclareAccount declareAccount ->
              declareAccountOutcome(declareAccount.command());
          case LedgerStep.PreflightEntry preflightEntry -> preflightOutcome(preflightEntry);
          case LedgerStep.PostEntry postEntry -> postEntryOutcome(postEntry);
          case LedgerStep.InspectBook ignored -> inspectBookOutcome();
          case LedgerStep.ListAccounts listAccounts -> listAccountsOutcome(listAccounts);
          case LedgerStep.GetPosting getPosting -> getPostingOutcome(getPosting);
          case LedgerStep.ListPostings listPostings -> listPostingsOutcome(listPostings);
          case LedgerStep.AccountBalance accountBalance -> accountBalanceOutcome(accountBalance);
          case LedgerStep.Assert assertion -> assertionOutcome(assertion.assertion());
        };
    return new LedgerJournalEntry(
        step.stepId(),
        step.operationId(),
        outcome.status(),
        startedAt,
        Instant.now(clock),
        outcome.facts(),
        outcome.failure());
  }

  private StepOutcome openBookOutcome() {
    return switch (bookSession.openBook(Instant.now(clock))) {
      case OpenBookResult.Opened opened ->
          StepOutcome.succeeded(new LedgerFact("initializedAt", opened.initializedAt().toString()));
      case OpenBookResult.Rejected rejected -> administrationRejection(rejected.rejection());
    };
  }

  private StepOutcome declareAccountOutcome(
      dev.erst.fingrind.contract.DeclareAccountCommand command) {
    return switch (new BookAdministrationService(bookSession, clock).declareAccount(command)) {
      case DeclareAccountResult.Declared declared ->
          StepOutcome.succeeded(
              new LedgerFact("accountCode", declared.account().accountCode().value()),
              new LedgerFact("normalBalance", declared.account().normalBalance().name()),
              new LedgerFact("active", Boolean.toString(declared.account().active())));
      case DeclareAccountResult.Rejected rejected -> administrationRejection(rejected.rejection());
    };
  }

  private StepOutcome preflightOutcome(LedgerStep.PreflightEntry step) {
    PostEntryResult result = postingService().preflight(step.command());
    if (result instanceof PostEntryResult.Rejected rejected) {
      return postingRejection(rejected.rejection());
    }
    PostEntryResult.PreflightAccepted accepted = (PostEntryResult.PreflightAccepted) result;
    return StepOutcome.succeeded(
        new LedgerFact("idempotencyKey", accepted.idempotencyKey().value()),
        new LedgerFact("effectiveDate", accepted.effectiveDate().toString()));
  }

  private StepOutcome postEntryOutcome(LedgerStep.PostEntry step) {
    PostEntryResult result = postingService().commit(step.command());
    if (result instanceof PostEntryResult.Rejected rejected) {
      return postingRejection(rejected.rejection());
    }
    PostEntryResult.Committed committed = (PostEntryResult.Committed) result;
    return StepOutcome.succeeded(
        new LedgerFact("postingId", committed.postingId().value()),
        new LedgerFact("idempotencyKey", committed.idempotencyKey().value()),
        new LedgerFact("effectiveDate", committed.effectiveDate().toString()),
        new LedgerFact("recordedAt", committed.recordedAt().toString()));
  }

  private StepOutcome inspectBookOutcome() {
    BookInspection inspection = new BookQueryService(bookSession).inspectBook();
    return StepOutcome.succeeded(
        new LedgerFact("state", inspection.status().name()),
        new LedgerFact("initialized", Boolean.toString(inspection.initialized())),
        new LedgerFact(
            "compatibleWithCurrentBinary",
            Boolean.toString(inspection.compatibleWithCurrentBinary())));
  }

  private StepOutcome listAccountsOutcome(LedgerStep.ListAccounts step) {
    AccountPage page = bookSession.listAccounts(step.query());
    return StepOutcome.succeeded(
        new LedgerFact("count", Integer.toString(page.accounts().size())),
        new LedgerFact("hasMore", Boolean.toString(page.hasMore())));
  }

  private StepOutcome getPostingOutcome(LedgerStep.GetPosting step) {
    return switch (new BookQueryService(bookSession).getPosting(step.postingId())) {
      case GetPostingResult.Found found ->
          StepOutcome.succeeded(postingFacts(found.postingFact()).toArray(LedgerFact[]::new));
      case GetPostingResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome listPostingsOutcome(LedgerStep.ListPostings step) {
    return switch (new BookQueryService(bookSession).listPostings(step.query())) {
      case ListPostingsResult.Listed listed ->
          StepOutcome.succeeded(
              new LedgerFact("count", Integer.toString(listed.page().postings().size())),
              new LedgerFact("hasMore", Boolean.toString(listed.page().hasMore())));
      case ListPostingsResult.Rejected rejected -> queryRejection(rejected.rejection());
    };
  }

  private StepOutcome accountBalanceOutcome(LedgerStep.AccountBalance step) {
    return switch (new BookQueryService(bookSession).accountBalance(step.query())) {
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
    boolean present = bookSession.findAccount(assertion.accountCode()).isPresent();
    return present
        ? StepOutcome.succeeded(new LedgerFact("accountCode", assertion.accountCode().value()))
        : assertionFailure(
            "Account is not declared.",
            new LedgerFact("accountCode", assertion.accountCode().value()));
  }

  private StepOutcome assertAccountActive(LedgerAssertion.AccountActive assertion) {
    Optional<dev.erst.fingrind.contract.DeclaredAccount> account =
        bookSession.findAccount(assertion.accountCode());
    if (account.isEmpty()) {
      return assertionFailure(
          "Account is not declared.",
          new LedgerFact("accountCode", assertion.accountCode().value()));
    }
    return account.orElseThrow().active()
        ? StepOutcome.succeeded(new LedgerFact("accountCode", assertion.accountCode().value()))
        : assertionFailure(
            "Account is not active.",
            new LedgerFact("accountCode", assertion.accountCode().value()));
  }

  private StepOutcome assertPostingExists(LedgerAssertion.PostingExists assertion) {
    boolean present = bookSession.findPosting(assertion.postingId()).isPresent();
    return present
        ? StepOutcome.succeeded(new LedgerFact("postingId", assertion.postingId().value()))
        : assertionFailure(
            "Posting does not exist.", new LedgerFact("postingId", assertion.postingId().value()));
  }

  private StepOutcome assertAccountBalance(LedgerAssertion.AccountBalanceEquals assertion) {
    AccountBalanceResult result =
        new BookQueryService(bookSession).accountBalance(assertion.query());
    if (result instanceof AccountBalanceResult.Rejected rejected) {
      return queryRejection(rejected.rejection());
    }
    AccountBalanceSnapshot snapshot = ((AccountBalanceResult.Reported) result).snapshot();
    Optional<CurrencyBalance> matchingBalance =
        snapshot.balances().stream()
            .filter(
                balance ->
                    balance.netAmount().currencyCode().equals(assertion.netAmount().currencyCode()))
            .findFirst();
    if (matchingBalance.isEmpty()) {
      return assertionFailure(
          "Expected currency balance bucket does not exist.",
          new LedgerFact("accountCode", assertion.accountCode().value()),
          new LedgerFact("currencyCode", assertion.netAmount().currencyCode().value()));
    }
    CurrencyBalance balance = matchingBalance.orElseThrow();
    boolean matchesAmount = balance.netAmount().equals(assertion.netAmount());
    boolean matchesSide = balance.balanceSide() == assertion.balanceSide();
    if (matchesAmount && matchesSide) {
      return StepOutcome.succeeded(balanceFacts(snapshot).facts().toArray(LedgerFact[]::new));
    }
    return assertionFailure(
        "Account balance does not match expected value.",
        new LedgerFact("accountCode", assertion.accountCode().value()),
        new LedgerFact("currencyCode", assertion.netAmount().currencyCode().value()),
        new LedgerFact("expectedNetAmount", assertion.netAmount().amount().toPlainString()),
        new LedgerFact("actualNetAmount", balance.netAmount().amount().toPlainString()),
        new LedgerFact("expectedBalanceSide", assertion.balanceSide().name()),
        new LedgerFact("actualBalanceSide", balance.balanceSide().name()));
  }

  private PostingApplicationService postingService() {
    return new PostingApplicationService(bookSession, postingIdGenerator, clock);
  }

  private static StepOutcome balanceFacts(AccountBalanceSnapshot snapshot) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(new LedgerFact("accountCode", snapshot.account().accountCode().value()));
    facts.add(new LedgerFact("bucketCount", Integer.toString(snapshot.balances().size())));
    for (CurrencyBalance balance : snapshot.balances()) {
      facts.add(new LedgerFact("currencyCode", balance.netAmount().currencyCode().value()));
      facts.add(new LedgerFact("debitTotal", balance.debitTotal().amount().toPlainString()));
      facts.add(new LedgerFact("creditTotal", balance.creditTotal().amount().toPlainString()));
      facts.add(new LedgerFact("netAmount", balance.netAmount().amount().toPlainString()));
      facts.add(new LedgerFact("balanceSide", balance.balanceSide().name()));
    }
    return StepOutcome.succeeded(facts.toArray(LedgerFact[]::new));
  }

  private static List<LedgerFact> postingFacts(PostingFact postingFact) {
    return List.of(
        new LedgerFact("postingId", postingFact.postingId().value()),
        new LedgerFact(
            "idempotencyKey",
            postingFact.provenance().requestProvenance().idempotencyKey().value()),
        new LedgerFact("effectiveDate", postingFact.journalEntry().effectiveDate().toString()),
        new LedgerFact("recordedAt", postingFact.provenance().recordedAt().toString()));
  }

  private static StepOutcome administrationRejection(BookAdministrationRejection rejection) {
    return StepOutcome.rejected(
        BookAdministrationRejection.wireCode(rejection), rejection.getClass().getSimpleName());
  }

  private static StepOutcome queryRejection(BookQueryRejection rejection) {
    return StepOutcome.rejected(
        BookQueryRejection.wireCode(rejection), rejection.getClass().getSimpleName());
  }

  private static StepOutcome postingRejection(PostingRejection rejection) {
    return StepOutcome.rejected(
        PostingRejection.wireCode(rejection), rejection.getClass().getSimpleName());
  }

  private static StepOutcome assertionFailure(String message, LedgerFact... facts) {
    return new StepOutcome(
        LedgerStepStatus.ASSERTION_FAILED,
        List.of(facts),
        Optional.of(new LedgerStepFailure(ASSERTION_FAILED, message, List.of(facts))));
  }

  private LedgerPlanResult result(
      String planId, LedgerPlanStatus status, Instant startedAt, List<LedgerJournalEntry> entries) {
    LedgerExecutionJournal journal =
        new LedgerExecutionJournal(
            planId, status, startedAt, Instant.now(clock), List.copyOf(entries));
    return new LedgerPlanResult(planId, status, journal);
  }

  private LedgerJournalEntry rejectedEntry(
      LedgerStep step, Instant startedAt, LedgerStepFailure failure) {
    return new LedgerJournalEntry(
        step.stepId(),
        step.operationId(),
        LedgerStepStatus.REJECTED,
        startedAt,
        Instant.now(clock),
        failure.facts(),
        Optional.of(failure));
  }

  private record StepOutcome(
      LedgerStepStatus status, List<LedgerFact> facts, Optional<LedgerStepFailure> failure) {
    StepOutcome {
      facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(failure, "failure");
    }

    static StepOutcome succeeded(LedgerFact... facts) {
      return new StepOutcome(LedgerStepStatus.SUCCEEDED, List.of(facts), Optional.empty());
    }

    static StepOutcome rejected(String code, String message) {
      return new StepOutcome(
          LedgerStepStatus.REJECTED,
          List.of(),
          Optional.of(new LedgerStepFailure(code, message, List.of())));
    }
  }
}
