package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.spi.AtomicBookStore;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Shared fixtures and seam doubles for split {@link LedgerPlanService} tests. */
final class LedgerPlanServiceTestSupport {
  static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-17T10:15:30Z"), ZoneOffset.UTC);

  private LedgerPlanServiceTestSupport() {}

  static void assertAssertionFailure(InMemoryBookSession bookSession, LedgerAssertion assertion) {
    LedgerPlanResult result =
        service(bookSession)
            .execute(
                new LedgerPlan(
                    planId("plan-assert"),
                    List.of(new LedgerStep.Assert(stepId("assert"), assertion))));

    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
  }

  static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_CLOCK.instant());
    return bookSession;
  }

  static InMemoryBookSession bookWithCommittedPosting() {
    InMemoryBookSession bookSession = initializedBook();
    bookSession.declareAccount(
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountRole(AccountType.ASSET, NormalBalance.DEBIT),
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountRole(AccountType.REVENUE, NormalBalance.CREDIT),
        FIXED_CLOCK.instant());
    PostEntryResult committed =
        new PostingApplicationService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK)
            .commit(postingCommand("idem-setup"));
    assertEquals(PostEntryResult.Committed.class, committed.getClass());
    return bookSession;
  }

  static LedgerPlanService service(AtomicBookStore bookSession) {
    return new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK);
  }

  static LedgerPlanId planId(String value) {
    return new LedgerPlanId(value);
  }

  static LedgerStepId stepId(String value) {
    return new LedgerStepId(value);
  }

  static boolean textFact(LedgerFact fact, String name, String value) {
    return fact instanceof LedgerFact.Text text
        && name.equals(text.name())
        && value.equals(text.value());
  }

  static boolean moneyFact(LedgerFact fact, String name, MonetaryAmount value) {
    return fact instanceof LedgerFact.Money money
        && name.equals(money.name())
        && value.equals(money.value());
  }

  static boolean countFact(LedgerFact fact, String name, int value) {
    return fact instanceof LedgerFact.Count count
        && name.equals(count.name())
        && value == count.value();
  }

  static boolean flagFact(LedgerFact fact, String name, boolean value) {
    return fact instanceof LedgerFact.Flag flag
        && name.equals(flag.name())
        && value == flag.value();
  }

  static boolean groupFact(
      LedgerFact fact,
      String groupName,
      String firstName,
      String firstValue,
      String secondName,
      String secondValue) {
    return fact instanceof LedgerFact.Group group
        && groupName.equals(group.name())
        && group.facts().stream().anyMatch(child -> textFact(child, firstName, firstValue))
        && group.facts().stream().anyMatch(child -> textFact(child, secondName, secondValue));
  }

  static boolean groupFact(
      LedgerFact fact,
      String groupName,
      String firstName,
      String firstValue,
      String secondName,
      MonetaryAmount secondValue) {
    return fact instanceof LedgerFact.Group group
        && groupName.equals(group.name())
        && group.facts().stream().anyMatch(child -> textFact(child, firstName, firstValue))
        && group.facts().stream().anyMatch(child -> moneyFact(child, secondName, secondValue));
  }

  static DeclareAccountCommand account(
      String accountCode,
      String accountName,
      AccountType accountType,
      NormalBalance normalBalance) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountRole(accountType, normalBalance));
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        dev.erst.fingrind.contract.bookkeeping.PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  static PostingCommand postingCommand(String idempotencyKey) {
    return BookkeepingPublishedLanguageTranslator.fromPublished(postEntryCommand(idempotencyKey));
  }

  static MonetaryAmount monetaryAmount(String currencyCode, String amountText) {
    return MonetaryAmount.of(Money.parse(currencyCode, amountText));
  }

  /** Shared delegating atomic store so failure fixtures only override the behavior under test. */
  abstract static class DelegatingAtomicBookStore implements AtomicBookStore, AutoCloseable {
    protected final InMemoryBookSession delegate = new InMemoryBookSession();

    @Override
    public BookLifecycleInspection inspectBook() {
      return delegate.inspectBook();
    }

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt) {
      return delegate.openBook(initializedAt);
    }

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      return delegate.declareAccount(
          accountCode, accountName, accountType, accountRole, declaredAt);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.RegisteredAccount> findAccount(
        AccountCode accountCode) {
      return delegate.findAccount(accountCode);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
        findExistingPosting(dev.erst.fingrind.core.IdempotencyKey idempotencyKey) {
      return delegate.findExistingPosting(idempotencyKey);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> findPosting(
        PostingId postingId) {
      return delegate.findPosting(postingId);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
        findReversalFor(PostingId priorPostingId) {
      return delegate.findReversalFor(priorPostingId);
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return delegate.postings(effectiveDateRange);
    }

    @Override
    public List<dev.erst.fingrind.executor.bookkeeping.RegisteredAccount> allAccounts() {
      return delegate.allAccounts();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return delegate.earliestPostingEffectiveDate();
    }

    @Override
    public Optional<LocalDate> closedThroughEffectiveDate() {
      return delegate.closedThroughEffectiveDate();
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage listAccounts(
        dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery query) {
      return delegate.listAccounts(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage listPostings(
        dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery query) {
      return delegate.listPostings(query);
    }

    @Override
    public java.util.Optional<dev.erst.fingrind.executor.bookkeeping.AccountBalanceView>
        accountBalance(dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria query) {
      return delegate.accountBalance(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.TrialBalanceView trialBalance(
        dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria query) {
      return delegate.trialBalance(query);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.AccountLedgerView accountLedger(
        dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria query,
        dev.erst.fingrind.executor.bookkeeping.RegisteredAccount account) {
      return delegate.accountLedger(query, account);
    }

    @Override
    public dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView periodSummary(
        dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria query) {
      return delegate.periodSummary(query);
    }

    @Override
    public PostingCommitResult commit(
        PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      return delegate.commit(postingDraft, postingIdGenerator);
    }

    @Override
    public PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      return delegate.closePeriod(periodCloseDraft, postingIdGenerator);
    }

    @Override
    public void beginLedgerPlanTransaction() {
      delegate.beginLedgerPlanTransaction();
    }

    @Override
    public void commitLedgerPlanTransaction() {
      delegate.commitLedgerPlanTransaction();
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      delegate.rollbackLedgerPlanTransaction();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  static final class ThrowingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt) {
      throw new IllegalStateException("boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws during declare-account after a successful open-book step. */
  static final class DeclareRuntimeFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        Instant declaredAt) {
      throw new IllegalStateException("declare boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws before any ledger-plan transaction begins. */
  static final class BeginFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void beginLedgerPlanTransaction() {
      throw new IllegalStateException("begin boom");
    }
  }

  /** Test-only seam split that throws while checking initialization before the first step runs. */
  static final class InitializationCheckFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public BookLifecycleInspection inspectBook() {
      throw new IllegalStateException("initialization boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that keeps queries uninitialized after a successful open-book step. */
  static final class ListAccountsRejectingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Missing(1);
    }
  }

  /** Test-only seam split that throws during commit so the outer finally rollback path runs. */
  static final class CommitFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    private boolean rollbackCalled;

    @Override
    public void commitLedgerPlanTransaction() {
      throw new IllegalStateException("commit boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }
  }

  /** Test-only seam split that throws during rollback after a deterministic plan failure. */
  static final class RollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {
    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }

  /**
   * Test-only seam split that throws during step execution and then throws again during rollback.
   */
  static final class RuntimeRollbackFailingLedgerPlanSession extends DelegatingAtomicBookStore {

    @Override
    public BookOpeningOutcome openBook(Instant initializedAt) {
      throw new IllegalStateException("boom");
    }

    @Override
    public void rollbackLedgerPlanTransaction() {
      throw new IllegalStateException("rollback boom");
    }
  }
}
