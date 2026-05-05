package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared fixtures and seam doubles for split {@link BookReadService} tests. */
final class BookReadServiceTestSupport {
  static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          FIXED_INSTANT);
  static final DeclaredAccount REVENUE_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          NormalBalance.CREDIT,
          true,
          FIXED_INSTANT);
  static final RegisteredAccount REGISTERED_CASH_ACCOUNT =
      new RegisteredAccount(
          CASH_ACCOUNT.accountCode(),
          CASH_ACCOUNT.accountName(),
          CASH_ACCOUNT.normalBalance(),
          CASH_ACCOUNT.active(),
          CASH_ACCOUNT.declaredAt());
  static final RegisteredAccount REGISTERED_REVENUE_ACCOUNT =
      new RegisteredAccount(
          REVENUE_ACCOUNT.accountCode(),
          REVENUE_ACCOUNT.accountName(),
          REVENUE_ACCOUNT.normalBalance(),
          REVENUE_ACCOUNT.active(),
          REVENUE_ACCOUNT.declaredAt());
  static final CurrencyBalance EUR_DEBIT_BALANCE =
      currencyBalance("10.00", "0", "10.00", BalanceSide.DEBIT);
  static final CurrencyBalance EUR_CREDIT_BALANCE =
      currencyBalance("0", "10.00", "10.00", BalanceSide.CREDIT);
  static final CurrencyBalance EUR_NET_ZERO =
      currencyBalance("10.00", "10.00", "0", BalanceSide.ZERO);

  private BookReadServiceTestSupport() {}

  static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  static CountingFindAccountBookSession initializedCountingBook() {
    CountingFindAccountBookSession bookSession = new CountingFindAccountBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        CASH_ACCOUNT.accountCode(),
        CASH_ACCOUNT.accountName(),
        CASH_ACCOUNT.normalBalance(),
        FIXED_INSTANT);
    bookSession.declareAccount(
        REVENUE_ACCOUNT.accountCode(),
        REVENUE_ACCOUNT.accountName(),
        REVENUE_ACCOUNT.normalBalance(),
        FIXED_INSTANT);
  }

  static CommittedPosting postingFact(String postingId, String idempotencyKey) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(
            EFFECTIVE_DATE,
            List.of(
                line(CASH_ACCOUNT.accountCode().value(), JournalLine.EntrySide.DEBIT, "10.00"),
                line(
                    REVENUE_ACCOUNT.accountCode().value(), JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineageModel.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyCode currencyCode = new CurrencyCode("EUR");
    return new CurrencyBalance(
        new Money(currencyCode, new BigDecimal(debitAmount)),
        new Money(currencyCode, new BigDecimal(creditAmount)),
        new Money(currencyCode, new BigDecimal(netAmount)),
        balanceSide);
  }

  /** Counts account lookups so account-balance tests can assert the read seam stays single-read. */
  static final class CountingFindAccountBookSession implements BookReadSession {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private int findAccountCalls;

    private void openBook(Instant initializedAt) {
      delegate.openBook(initializedAt);
    }

    void commit(CommittedPosting postingFact) {
      delegate.commit(postingFact);
    }

    InMemoryBookSession delegate() {
      return delegate;
    }

    @Override
    public BookInspection inspectBook() {
      return delegate.inspectBook();
    }

    @Override
    public boolean isInitialized() {
      return delegate.isInitialized();
    }

    @Override
    public AccountPage listAccounts(ListAccountsQuery query) {
      return delegate.listAccounts(query);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      findAccountCalls++;
      return delegate.findAccount(accountCode);
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return delegate.findPosting(postingId);
    }

    @Override
    public PostingPage listPostings(ListPostingsQuery query) {
      return delegate.listPostings(query);
    }

    @Override
    public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
      return delegate.accountBalance(query);
    }

    @Override
    public TrialBalanceReport trialBalance(TrialBalanceQuery query) {
      return delegate.trialBalance(query);
    }

    @Override
    public AccountLedgerReport accountLedger(AccountLedgerQuery query, RegisteredAccount account) {
      return delegate.accountLedger(query, account);
    }

    @Override
    public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
      return delegate.periodSummary(query);
    }

    int findAccountCalls() {
      return findAccountCalls;
    }

    void resetFindAccountCalls() {
      findAccountCalls = 0;
    }
  }
}
