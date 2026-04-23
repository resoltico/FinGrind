package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanId;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.LedgerStepId;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.PostingLineage;
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
            .commit(postEntryCommand("idem-setup"));
    assertEquals(PostEntryResult.Committed.class, committed.getClass());
    return bookSession;
  }

  static LedgerPlanService service(LedgerPlanSession bookSession) {
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
        PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  /** In-memory session that throws during open-book to exercise rollback-on-runtime-failure. */
  static final class ThrowingLedgerPlanSession implements LedgerPlanSession, AutoCloseable {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private final BookAdministrationSession throwingAdministrationSession =
        new BookAdministrationSession() {
          @Override
          public OpenBookResult openBook(Instant initializedAt) {
            throw new IllegalStateException("boom");
          }

          @Override
          public dev.erst.fingrind.contract.DeclareAccountResult declareAccount(
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
          public dev.erst.fingrind.contract.AccountPage listAccounts(ListAccountsQuery query) {
            return delegate.listAccounts(query);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.DeclaredAccount> findAccount(
              AccountCode accountCode) {
            return delegate.findAccount(accountCode);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.PostingFact> findPosting(PostingId postingId) {
            return delegate.findPosting(postingId);
          }

          @Override
          public dev.erst.fingrind.contract.PostingPage listPostings(ListPostingsQuery query) {
            return delegate.listPostings(query);
          }

          @Override
          public Optional<dev.erst.fingrind.contract.AccountBalanceSnapshot> accountBalance(
              AccountBalanceQuery query) {
            return delegate.accountBalance(query);
          }

          @Override
          public dev.erst.fingrind.contract.TrialBalanceReport trialBalance(
              dev.erst.fingrind.contract.TrialBalanceQuery query) {
            return delegate.trialBalance(query);
          }

          @Override
          public dev.erst.fingrind.contract.AccountLedgerReport accountLedger(
              dev.erst.fingrind.contract.AccountLedgerQuery query,
              dev.erst.fingrind.contract.DeclaredAccount account) {
            return delegate.accountLedger(query, account);
          }

          @Override
          public dev.erst.fingrind.contract.PeriodSummaryReport periodSummary(
              dev.erst.fingrind.contract.PeriodSummaryQuery query) {
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
}
