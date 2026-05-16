package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.declaredAccount;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared fixtures and seam doubles for split {@link BookReadService} tests. */
final class BookReadServiceTestSupport {
  static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  static final DeclaredAccount CASH_ACCOUNT =
      declaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          NormalBalance.DEBIT,
          true,
          FIXED_INSTANT);
  static final DeclaredAccount REVENUE_ACCOUNT =
      declaredAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          AccountType.REVENUE,
          NormalBalance.CREDIT,
          true,
          FIXED_INSTANT);
  static final RegisteredAccount REGISTERED_CASH_ACCOUNT =
      registeredAccount(
          CASH_ACCOUNT.accountCode(),
          CASH_ACCOUNT.accountName(),
          CASH_ACCOUNT.accountType(),
          CASH_ACCOUNT.normalBalance(),
          CASH_ACCOUNT.active(),
          CASH_ACCOUNT.declaredAt());
  static final RegisteredAccount REGISTERED_REVENUE_ACCOUNT =
      registeredAccount(
          REVENUE_ACCOUNT.accountCode(),
          REVENUE_ACCOUNT.accountName(),
          REVENUE_ACCOUNT.accountType(),
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
    bookSession.openBook(FIXED_INSTANT, bookIdentity());
    return bookSession;
  }

  static CountingFindAccountBookSession initializedCountingBook() {
    CountingFindAccountBookSession bookSession = new CountingFindAccountBookSession();
    bookSession.openBook(FIXED_INSTANT, bookIdentity());
    return bookSession;
  }

  static BookReadService readService(InMemoryBookSession bookSession) {
    return new BookReadService(bookSession);
  }

  static BookkeepingReadService localReadService(InMemoryBookSession bookSession) {
    return new BookkeepingReadService(bookSession);
  }

  static BookReadService readService(CountingFindAccountBookSession bookSession) {
    return new BookReadService(bookSession);
  }

  static BookkeepingReadService localReadService(CountingFindAccountBookSession bookSession) {
    return new BookkeepingReadService(bookSession);
  }

  static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        CASH_ACCOUNT.accountCode(),
        CASH_ACCOUNT.accountName(),
        CASH_ACCOUNT.accountType(),
        accountRole(CASH_ACCOUNT.accountType(), CASH_ACCOUNT.normalBalance()),
        accountTaxonomy(CASH_ACCOUNT.accountType()),
        FIXED_INSTANT);
    bookSession.declareAccount(
        REVENUE_ACCOUNT.accountCode(),
        REVENUE_ACCOUNT.accountName(),
        REVENUE_ACCOUNT.accountType(),
        accountRole(REVENUE_ACCOUNT.accountType(), REVENUE_ACCOUNT.normalBalance()),
        accountTaxonomy(REVENUE_ACCOUNT.accountType()),
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
        PostingKind.STANDARD,
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
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(Money.parse("EUR", debitAmount), Money.parse("EUR", creditAmount));
    if (!balance.netAmount().equals(Money.parse("EUR", netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  /** Counts account lookups so account-balance tests can assert the read seam stays single-read. */
  static final class CountingFindAccountBookSession implements BookkeepingReadStore {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private int findAccountCalls;

    BookOpeningOutcome openBook(
        Instant initializedAt, dev.erst.fingrind.core.BookIdentity bookIdentity) {
      return delegate.openBook(initializedAt, bookIdentity);
    }

    AccountDeclarationOutcome declareAccount(
        AccountCode accountCode,
        AccountName accountName,
        AccountType accountType,
        AccountRole accountRole,
        AccountTaxonomy accountTaxonomy,
        Instant declaredAt) {
      return delegate.declareAccount(
          accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
    }

    PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
      return delegate.commit(postingDraft, postingIdGenerator);
    }

    PostingCommitResult commit(CommittedPosting postingFact) {
      return delegate.commit(postingFact);
    }

    InMemoryBookSession delegate() {
      return delegate;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return delegate.inspectBook();
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      return delegate.listAccounts(query);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      findAccountCalls++;
      return delegate.findAccount(accountCode);
    }

    @Override
    public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return delegate.findExistingPosting(idempotencyKey);
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return delegate.findPosting(postingId);
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return delegate.findReversalFor(priorPostingId);
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return delegate.allAccounts();
    }

    List<CommittedPosting> postings(dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
      return delegate.postings(effectiveDateRange);
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
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      return delegate.accountTotals(effectiveDateRange, postingCoverage);
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      return delegate.trialBalance(query);
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      return delegate.accountLedger(query, account);
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      return delegate.periodSummary(query);
    }

    PeriodCloseOutcome closePeriod(
        PeriodCloseDraft periodCloseDraft, PostingIdGenerator postingIdGenerator) {
      return delegate.closePeriod(periodCloseDraft, postingIdGenerator);
    }

    int findAccountCalls() {
      return findAccountCalls;
    }

    void resetFindAccountCalls() {
      findAccountCalls = 0;
    }
  }
}
