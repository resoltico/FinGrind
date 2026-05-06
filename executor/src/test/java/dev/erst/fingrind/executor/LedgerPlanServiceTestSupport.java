package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import java.math.BigDecimal;
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
        NormalBalance.DEBIT,
        FIXED_CLOCK.instant());
    bookSession.declareAccount(
        new AccountCode("2000"),
        new AccountName("Revenue"),
        NormalBalance.CREDIT,
        FIXED_CLOCK.instant());
    PostEntryResult committed =
        new PostingApplicationService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK)
            .commit(postingCommand("idem-setup"));
    assertEquals(PostEntryResult.Committed.class, committed.getClass());
    return bookSession;
  }

  static PublishedLedgerPlanService service(LedgerPlanSession bookSession) {
    return new PublishedLedgerPlanService(
        new LedgerPlanService(bookSession, () -> new PostingId("posting-1"), FIXED_CLOCK));
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

  static DeclareAccountCommand account(
      String accountCode, String accountName, NormalBalance normalBalance) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode), new AccountName(accountName), normalBalance);
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))))),
        dev.erst.fingrind.contract.PostingLineage.direct(),
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

  /** Test-only adapter that accepts published plan fixtures while the service stays internal. */
  static final class PublishedLedgerPlanService {
    private final LedgerPlanService delegate;

    private PublishedLedgerPlanService(LedgerPlanService delegate) {
      this.delegate = delegate;
    }

    LedgerPlanResult execute(LedgerPlan plan) {
      return delegate.execute(BookWorkflowPublishedLanguageTranslator.fromPublished(plan));
    }
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  static final class ThrowingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookAdministrationSession throwingAdministrationSession =
        new BookAdministrationSession() {
          @Override
          public BookOpeningOutcome openBook(Instant initializedAt) {
            throw new IllegalStateException("boom");
          }

          @Override
          public AccountDeclarationOutcome declareAccount(
              AccountCode accountCode,
              AccountName accountName,
              NormalBalance normalBalance,
              Instant declaredAt) {
            return delegate.declareAccount(accountCode, accountName, normalBalance, declaredAt);
          }
        };
    private boolean rollbackCalled;

    @Override
    public BookAdministrationSession administrationSession() {
      return throwingAdministrationSession;
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
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
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** Test-only seam split that throws during declare-account after a successful open-book step. */
  static final class DeclareRuntimeFailingLedgerPlanSession
      implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookAdministrationSession throwingAdministrationSession =
        new BookAdministrationSession() {
          @Override
          public BookOpeningOutcome openBook(Instant initializedAt) {
            return delegate.openBook(initializedAt);
          }

          @Override
          public AccountDeclarationOutcome declareAccount(
              AccountCode accountCode,
              AccountName accountName,
              NormalBalance normalBalance,
              Instant declaredAt) {
            throw new IllegalStateException("declare boom");
          }
        };
    private boolean rollbackCalled;

    @Override
    public BookAdministrationSession administrationSession() {
      return throwingAdministrationSession;
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
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
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** Test-only seam split that throws before any ledger-plan transaction begins. */
  static final class BeginFailingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
    }

    @Override
    public void beginLedgerPlanTransaction() {
      throw new IllegalStateException("begin boom");
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

  /** Test-only seam split that throws while checking initialization before the first step runs. */
  static final class InitializationCheckFailingLedgerPlanSession
      implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookReadSession throwingReadSession =
        new BookReadSession() {
          @Override
          public dev.erst.fingrind.contract.BookInspection inspectBook() {
            return delegate.inspectBook();
          }

          @Override
          public boolean isInitialized() {
            throw new IllegalStateException("initialization boom");
          }

          @Override
          public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
            return delegate.listAccounts(query);
          }

          @Override
          public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
            return delegate.findAccount(accountCode);
          }

          @Override
          public Optional<CommittedPosting> findPosting(PostingId postingId) {
            return delegate.findPosting(postingId);
          }

          @Override
          public PostingHistoryPage listPostings(PostingHistoryQuery query) {
            return delegate.listPostings(query);
          }

          @Override
          public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
            return delegate.accountBalance(query);
          }

          @Override
          public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
            return delegate.trialBalance(query);
          }

          @Override
          public AccountLedgerView accountLedger(
              AccountLedgerCriteria query, RegisteredAccount account) {
            return delegate.accountLedger(query, account);
          }

          @Override
          public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
            return delegate.periodSummary(query);
          }
        };
    private boolean rollbackCalled;

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return throwingReadSession;
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
      rollbackCalled = true;
      delegate.rollbackLedgerPlanTransaction();
    }

    boolean rollbackCalled() {
      return rollbackCalled;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** Test-only seam split that keeps queries uninitialized after a successful open-book step. */
  static final class ListAccountsRejectingLedgerPlanSession
      implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookReadSession rejectingQuerySession =
        new BookReadSession() {
          @Override
          public dev.erst.fingrind.contract.BookInspection inspectBook() {
            return delegate.inspectBook();
          }

          @Override
          public boolean isInitialized() {
            return false;
          }

          @Override
          public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
            return delegate.listAccounts(query);
          }

          @Override
          public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
            return delegate.findAccount(accountCode);
          }

          @Override
          public Optional<CommittedPosting> findPosting(PostingId postingId) {
            return delegate.findPosting(postingId);
          }

          @Override
          public PostingHistoryPage listPostings(PostingHistoryQuery query) {
            return delegate.listPostings(query);
          }

          @Override
          public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
            return delegate.accountBalance(query);
          }

          @Override
          public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
            return delegate.trialBalance(query);
          }

          @Override
          public AccountLedgerView accountLedger(
              AccountLedgerCriteria query, RegisteredAccount account) {
            return delegate.accountLedger(query, account);
          }

          @Override
          public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
            return delegate.periodSummary(query);
          }
        };

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return rejectingQuerySession;
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

  /** Test-only seam split that throws during commit so the outer finally rollback path runs. */
  static final class CommitFailingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private boolean rollbackCalled;

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
    }

    @Override
    public void beginLedgerPlanTransaction() {
      delegate.beginLedgerPlanTransaction();
    }

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

    @Override
    public void close() {
      delegate.close();
    }
  }

  /** Test-only seam split that throws during rollback after a deterministic plan failure. */
  static final class RollbackFailingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();

    @Override
    public BookAdministrationSession administrationSession() {
      return delegate.administrationSession();
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
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
      throw new IllegalStateException("rollback boom");
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  /**
   * Test-only seam split that throws during step execution and then throws again during rollback.
   */
  static final class RuntimeRollbackFailingLedgerPlanSession
      implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookAdministrationSession throwingAdministrationSession =
        new BookAdministrationSession() {
          @Override
          public BookOpeningOutcome openBook(Instant initializedAt) {
            throw new IllegalStateException("boom");
          }

          @Override
          public AccountDeclarationOutcome declareAccount(
              AccountCode accountCode,
              AccountName accountName,
              NormalBalance normalBalance,
              Instant declaredAt) {
            return delegate.declareAccount(accountCode, accountName, normalBalance, declaredAt);
          }
        };

    @Override
    public BookAdministrationSession administrationSession() {
      return throwingAdministrationSession;
    }

    @Override
    public PostingBookSession postingSession() {
      return delegate.postingSession();
    }

    @Override
    public BookReadSession readSession() {
      return delegate.readSession();
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
      throw new IllegalStateException("rollback boom");
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
