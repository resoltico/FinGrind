package dev.erst.fingrind.executor.bookkeeping.read;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceRowView;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for trial-balance comparative snapshot derivation. */
class BookkeepingReadQuerySupportTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final LocalDate CURRENT_AS_OF = LocalDate.parse("2026-04-30");
  private static final LocalDate COMPARATIVE_AS_OF = LocalDate.parse("2025-04-30");

  @Test
  void trialBalanceView_fetchesOne_dedicated_comparative_snapshot_when_requested() {
    TrialBalanceRowView currentRow = row("1000", "Cash", "10.00", "0.00");
    TrialBalanceRowView comparativeRow = row("1000", "Cash", "7.50", "0.00");
    TrialBalanceView currentView =
        new TrialBalanceView(
            bookIdentity(),
            Optional.of(CURRENT_AS_OF),
            Optional.of(CURRENT_AS_OF),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(currentRow),
            List.of());
    TrialBalanceView comparativeView =
        new TrialBalanceView(
            bookIdentity(),
            Optional.of(COMPARATIVE_AS_OF),
            Optional.of(COMPARATIVE_AS_OF),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(comparativeRow),
            List.of());
    TrialBalanceSupportStore store = new TrialBalanceSupportStore(currentView, comparativeView);
    EffectiveDateRange comparativeRange = EffectiveDateRange.to(COMPARATIVE_AS_OF);

    TrialBalanceView resolvedView =
        BookkeepingReadQuerySupport.trialBalanceView(
            store,
            new TrialBalanceCriteria(
                Optional.of(CURRENT_AS_OF),
                PostingCoverage.ALL_POSTING_KINDS,
                ComparativeSelection.range(comparativeRange)));

    assertEquals(comparativeRange, resolvedView.comparativeEffectiveDateRange());
    assertEquals(List.of(comparativeRow), resolvedView.comparativeRows());
    assertEquals(
        new TrialBalanceCriteria(
            Optional.of(COMPARATIVE_AS_OF),
            PostingCoverage.ALL_POSTING_KINDS,
            ComparativeSelection.none()),
        store.comparativeQuery());
    assertEquals(2, store.trialBalanceCalls());
  }

  @Test
  void trialBalanceView_skips_one_comparative_probe_when_comparatives_are_disabled() {
    TrialBalanceView currentView =
        new TrialBalanceView(
            bookIdentity(),
            Optional.of(CURRENT_AS_OF),
            Optional.of(CURRENT_AS_OF),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(row("1000", "Cash", "10.00", "0.00")),
            List.of());
    TrialBalanceSupportStore store = new TrialBalanceSupportStore(currentView, currentView);

    TrialBalanceView resolvedView =
        BookkeepingReadQuerySupport.trialBalanceView(
            store,
            new TrialBalanceCriteria(
                Optional.of(CURRENT_AS_OF),
                PostingCoverage.ALL_POSTING_KINDS,
                ComparativeSelection.none()));

    assertEquals(EffectiveDateRange.unbounded(), resolvedView.comparativeEffectiveDateRange());
    assertEquals(List.of(), resolvedView.comparativeRows());
    assertEquals(1, store.trialBalanceCalls());
  }

  private static TrialBalanceRowView row(
      String accountCode, String accountName, String debitAmount, String creditAmount) {
    return new TrialBalanceRowView(
        new RegisteredAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            AccountType.ASSET,
            accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
            true,
            DECLARED_AT),
        CurrencyBalance.ofTotals(
            Money.parse("EUR", debitAmount), Money.parse("EUR", creditAmount)));
  }

  /** Tiny read-store double used only to verify trial-balance comparative orchestration. */
  private static final class TrialBalanceSupportStore implements BookkeepingReadStore {
    private final TrialBalanceView currentView;
    private final TrialBalanceView comparativeView;
    private int trialBalanceCalls;
    private TrialBalanceCriteria comparativeQuery =
        new TrialBalanceCriteria(
            Optional.of(COMPARATIVE_AS_OF),
            PostingCoverage.ALL_POSTING_KINDS,
            ComparativeSelection.none());

    private TrialBalanceSupportStore(
        TrialBalanceView currentView, TrialBalanceView comparativeView) {
      this.currentView = currentView;
      this.comparativeView = comparativeView;
    }

    private int trialBalanceCalls() {
      return trialBalanceCalls;
    }

    private TrialBalanceCriteria comparativeQuery() {
      return comparativeQuery;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 2, 2, DECLARED_AT, bookIdentity());
    }

    @Override
    public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public java.util.Map<AccountCode, RegisteredAccount> findAccounts(
        java.util.Set<AccountCode> accountCodes) {
      return java.util.Map.of();
    }

    @Override
    public List<RegisteredAccount> allAccounts() {
      return List.of();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public PostingHistoryPage listPostings(PostingHistoryQuery query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountCurrencyTotals> accountTotals(
        EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> latestPostingEffectiveDate() {
      return Optional.of(CURRENT_AS_OF);
    }

    @Override
    public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
      trialBalanceCalls += 1;
      if (trialBalanceCalls == 1) {
        return currentView;
      }
      comparativeQuery = query;
      return comparativeView;
    }

    @Override
    public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
      throw new UnsupportedOperationException();
    }
  }
}
