package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountLedgerResult;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PeriodSummaryResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceResult;
import dev.erst.fingrind.contract.TrialBalanceRow;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookReadService}. */
class BookReadServiceTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-07");
  private static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          FIXED_INSTANT);
  private static final DeclaredAccount REVENUE_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          NormalBalance.CREDIT,
          true,
          FIXED_INSTANT);
  private static final CurrencyBalance EUR_DEBIT_BALANCE =
      currencyBalance("10.00", "0", "10.00", BalanceSide.DEBIT);
  private static final CurrencyBalance EUR_CREDIT_BALANCE =
      currencyBalance("0", "10.00", "10.00", BalanceSide.CREDIT);
  private static final CurrencyBalance EUR_NET_ZERO =
      currencyBalance("10.00", "10.00", "0", BalanceSide.ZERO);

  @Test
  void constructor_rejectsNullSession() {
    assertThrows(NullPointerException.class, () -> new BookReadService(null));
  }

  @Test
  void inspectBook_delegatesToSessionInspection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(bookSession.inspectBook(), service.inspectBook());
    }
  }

  @Test
  void listAccounts_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listAccounts(new ListAccountsQuery(50, 0)));
    }
  }

  @Test
  void listAccounts_returnsDeclaredAccountsWhenInitialized() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListAccountsResult.Listed(
              new AccountPage(List.of(CASH_ACCOUNT, REVENUE_ACCOUNT), 50, 0, false)),
          service.listAccounts(new ListAccountsQuery(50, 0)));
    }
  }

  @Test
  void getPosting_rejectsUninitializedAndMissingPosting() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.getPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new GetPostingResult.Rejected(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-1"))),
          service.getPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void listPostings_rejectsUnknownFilteredAccount() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListPostingsResult.Rejected(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.of(new AccountCode("9999")),
                  Optional.empty(),
                  Optional.empty(),
                  20,
                  Optional.empty())));
    }
  }

  @Test
  void listPostings_rejectsUninitializedBookAndListsCommittedPostings() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListPostingsResult.Listed(
              new PostingPage(List.of(postingFact), 20, Optional.empty())),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty())));
      assertEquals(
          new ListPostingsResult.Listed(
              new PostingPage(List.of(postingFact), 20, Optional.empty())),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.of(CASH_ACCOUNT.accountCode()),
                  Optional.empty(),
                  Optional.empty(),
                  20,
                  Optional.empty())));
    }
  }

  @Test
  void getPostingAndAccountBalance_returnCommittedSnapshots() {
    try (CountingFindAccountBookSession bookSession = initializedCountingBook()) {
      declareDefaultAccounts(bookSession.delegate());
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      bookSession.resetFindAccountCalls();
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new GetPostingResult.Found(postingFact), service.getPosting(new PostingId("posting-1")));
      assertEquals(
          new AccountBalanceResult.Reported(
              new AccountBalanceSnapshot(
                  CASH_ACCOUNT, Optional.empty(), Optional.empty(), List.of(EUR_DEBIT_BALANCE))),
          service.accountBalance(
              new AccountBalanceQuery(
                  CASH_ACCOUNT.accountCode(), Optional.empty(), Optional.empty())));
      assertEquals(0, bookSession.findAccountCalls());
    }
  }

  @Test
  void findHelpers_returnEmptyForUninitializedBooksAndDelegateWhenInitialized() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(Optional.empty(), service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(Optional.empty(), service.findPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(Optional.of(CASH_ACCOUNT), service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(Optional.of(postingFact), service.findPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void accountBalance_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountBalance(
              new AccountBalanceQuery(
                  CASH_ACCOUNT.accountCode(), Optional.empty(), Optional.empty())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new AccountBalanceResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountBalance(
              new AccountBalanceQuery(
                  CASH_ACCOUNT.accountCode(), Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void trialBalance_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.trialBalance(new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE))));
    }
  }

  @Test
  void trialBalance_reportsExpectedSnapshot() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(postingFact("posting-1", "idem-1"));
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new TrialBalanceResult.Reported(
              new TrialBalanceReport(
                  Optional.of(EFFECTIVE_DATE),
                  List.of(
                      new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new TrialBalanceRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)))),
          service.trialBalance(new TrialBalanceQuery(Optional.of(EFFECTIVE_DATE))));
    }
  }

  @Test
  void accountLedger_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new AccountLedgerResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountLedger(
              new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new AccountLedgerResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountLedger(
              new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), EffectiveDateRange.unbounded())));
    }
  }

  @Test
  void accountLedger_reportsOpeningEntriesAndClosingBalances() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new AccountLedgerResult.Reported(
              new AccountLedgerReport(
                  CASH_ACCOUNT,
                  EffectiveDateRange.of(Optional.of(EFFECTIVE_DATE), Optional.of(EFFECTIVE_DATE)),
                  List.of(),
                  List.of(
                      new AccountLedgerEntry(
                          postingFact,
                          EUR_DEBIT_BALANCE,
                          new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                          BalanceSide.DEBIT)),
                  List.of(EUR_DEBIT_BALANCE))),
          service.accountLedger(
              new AccountLedgerQuery(
                  CASH_ACCOUNT.accountCode(),
                  Optional.of(EFFECTIVE_DATE),
                  Optional.of(EFFECTIVE_DATE))));
    }
  }

  @Test
  void periodSummary_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.periodSummary(new PeriodSummaryQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
    }
  }

  @Test
  void periodSummary_reportsCurrencyAndAccountActivity() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(postingFact("posting-1", "idem-1"));
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new PeriodSummaryResult.Reported(
              new PeriodSummaryReport(
                  EFFECTIVE_DATE,
                  EFFECTIVE_DATE,
                  1,
                  2,
                  2,
                  List.of(new PeriodCurrencySummary(EUR_NET_ZERO)),
                  List.of(
                      new PeriodAccountActivityRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE),
                      new PeriodAccountActivityRow(REVENUE_ACCOUNT, EUR_CREDIT_BALANCE)))),
          service.periodSummary(new PeriodSummaryQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
    }
  }

  @Test
  void readMethods_rejectNullInputs() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertThrows(NullPointerException.class, () -> service.listAccounts(null));
      assertThrows(NullPointerException.class, () -> service.getPosting(null));
      assertThrows(NullPointerException.class, () -> service.findAccount(null));
      assertThrows(NullPointerException.class, () -> service.findPosting(null));
      assertThrows(NullPointerException.class, () -> service.listPostings(null));
      assertThrows(NullPointerException.class, () -> service.accountBalance(null));
      assertThrows(NullPointerException.class, () -> service.trialBalance(null));
      assertThrows(NullPointerException.class, () -> service.accountLedger(null));
      assertThrows(NullPointerException.class, () -> service.periodSummary(null));
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  private static CountingFindAccountBookSession initializedCountingBook() {
    CountingFindAccountBookSession bookSession = new CountingFindAccountBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
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

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(
            EFFECTIVE_DATE,
            List.of(
                line(CASH_ACCOUNT.accountCode().value(), JournalLine.EntrySide.DEBIT, "10.00"),
                line(
                    REVENUE_ACCOUNT.accountCode().value(), JournalLine.EntrySide.CREDIT, "10.00"))),
        PostingLineage.direct(),
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

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static CurrencyBalance currencyBalance(
      String debitAmount, String creditAmount, String netAmount, BalanceSide balanceSide) {
    CurrencyCode currencyCode = new CurrencyCode("EUR");
    return new CurrencyBalance(
        new Money(currencyCode, new BigDecimal(debitAmount)),
        new Money(currencyCode, new BigDecimal(creditAmount)),
        new Money(currencyCode, new BigDecimal(netAmount)),
        balanceSide);
  }

  /** Counts account lookups so account-balance tests can assert the read seam stays single-read. */
  private static final class CountingFindAccountBookSession implements BookReadSession {
    private final InMemoryBookSession delegate = new InMemoryBookSession();
    private int findAccountCalls;

    private void openBook(Instant initializedAt) {
      delegate.openBook(initializedAt);
    }

    private void commit(PostingFact postingFact) {
      delegate.commit(postingFact);
    }

    private InMemoryBookSession delegate() {
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
    public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
      findAccountCalls++;
      return delegate.findAccount(accountCode);
    }

    @Override
    public Optional<PostingFact> findPosting(PostingId postingId) {
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
    public AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
      return delegate.accountLedger(query, account);
    }

    @Override
    public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
      return delegate.periodSummary(query);
    }

    private int findAccountCalls() {
      return findAccountCalls;
    }

    private void resetFindAccountCalls() {
      findAccountCalls = 0;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
