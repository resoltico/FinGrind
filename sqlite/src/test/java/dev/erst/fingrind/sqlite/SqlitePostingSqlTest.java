package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCursor;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for query-shape builders in {@link SqlitePostingSql}. */
class SqlitePostingSqlTest {
  private static final String NON_CLOSING_POSTING_KIND_FILTER =
      "posting_fact.posting_kind not in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')";

  @Test
  void listPostings_includesOnlyRequestedFilters() {
    String unfiltered =
        SqlitePostingSql.listPostings(
            new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty()));
    String fullyFiltered =
        SqlitePostingSql.listPostings(
            new PostingHistoryQuery(
                Optional.of(new AccountCode("1000")),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                50,
                Optional.of(
                    new PostingHistoryCursor(
                        LocalDate.parse("2026-04-15"),
                        Instant.parse("2026-04-15T10:15:30Z"),
                        new PostingId("posting-1")))));

    assertFalse(unfiltered.contains("journal_line.account_code = ?"));
    assertFalse(unfiltered.contains("effective_date >= ?"));
    assertFalse(unfiltered.contains("effective_date <= ?"));
    assertTrue(
        fullyFiltered.contains(
            """
             and exists (
                 select 1
                 from journal_line
                 where journal_line.posting_id = posting_fact.posting_id
                   and journal_line.account_code = ?
             )
            """));
    assertTrue(fullyFiltered.contains(" and effective_date >= ?"));
    assertTrue(fullyFiltered.contains(" and effective_date <= ?"));
    assertTrue(fullyFiltered.contains(" effective_date < ?"));
    assertFalse(unfiltered.contains(" effective_date < ?"));
  }

  @Test
  void loadAccountLinesForBalance_includesOnlyRequestedDateFilters() {
    String unfiltered =
        SqlitePostingSql.loadAccountLinesForBalance(
            AccountBalanceCriteria.unbounded(new AccountCode("1000")));
    String nonClosingOnly =
        SqlitePostingSql.loadAccountLinesForBalance(
            AccountBalanceCriteria.unbounded(
                new AccountCode("1000"), PostingCoverage.NON_CLOSING_POSTINGS));
    String fullyFiltered =
        SqlitePostingSql.loadAccountLinesForBalance(
            new AccountBalanceCriteria(
                new AccountCode("1000"),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30")));

    assertFalse(unfiltered.contains("posting_fact.effective_date >= ?"));
    assertFalse(unfiltered.contains("posting_fact.effective_date <= ?"));
    assertFalse(unfiltered.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(nonClosingOnly.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date >= ?"));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date <= ?"));
  }

  @Test
  void loadTrialBalanceLines_andAccountLedgerQueries_includeOnlyRequestedFilters() {
    String unfilteredTrialBalance =
        SqlitePostingSql.loadTrialBalanceLines(
            SqliteStoreTestIntrospectionSupport.trialBalanceCriteria(Optional.empty()));
    String filteredTrialBalance =
        SqlitePostingSql.loadTrialBalanceLines(
            SqliteStoreTestIntrospectionSupport.trialBalanceCriteria(
                Optional.of(LocalDate.parse("2026-04-30"))));
    String unboundedLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            SqliteStoreTestIntrospectionSupport.accountLedgerCriteria(
                new AccountCode("1000"), null, null));
    String nonClosingLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerCriteria(
                new AccountCode("1000"),
                EffectiveDateRange.unbounded(),
                PostingCoverage.NON_CLOSING_POSTINGS,
                50,
                Optional.empty()));
    String lowerBoundLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerCriteria(
                new AccountCode("1000"),
                EffectiveDateRange.of(LocalDate.parse("2026-04-01"), null),
                PostingCoverage.ALL_POSTING_KINDS,
                50,
                Optional.empty()));
    String upperBoundLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerCriteria(
                new AccountCode("1000"),
                EffectiveDateRange.of(null, LocalDate.parse("2026-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                50,
                Optional.empty()));
    String boundedLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerCriteria(
                new AccountCode("1000"),
                EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
                PostingCoverage.ALL_POSTING_KINDS,
                50,
                Optional.empty()));
    AccountLedgerCriteria cursorCriteria =
        new AccountLedgerCriteria(
            new AccountCode("1000"),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            50,
            Optional.of(
                new AccountLedgerCursor(
                    LocalDate.parse("2026-04-15"),
                    Instant.parse("2026-04-15T12:00:00Z"),
                    new PostingId("posting-1"))));
    String cursorLedger = SqlitePostingSql.listPostingsForAccountLedger(cursorCriteria);
    String priorBalanceLedger = SqlitePostingSql.loadAccountLedgerPriorBalances(cursorCriteria);
    String unboundedNonClosingPriorBalanceLedger =
        SqlitePostingSql.loadAccountLedgerPriorBalances(
            new AccountLedgerCriteria(
                new AccountCode("1000"),
                EffectiveDateRange.unbounded(),
                PostingCoverage.NON_CLOSING_POSTINGS,
                50,
                cursorCriteria.cursor()));

    assertFalse(unfilteredTrialBalance.contains("posting_fact.effective_date <= ?"));
    assertTrue(filteredTrialBalance.contains(" and posting_fact.effective_date <= ?"));
    assertTrue(unfilteredTrialBalance.contains("account.cash_flow_asset_classification"));

    assertFalse(unboundedLedger.contains("effective_date >= ?"));
    assertFalse(unboundedLedger.contains("effective_date <= ?"));
    assertFalse(unboundedLedger.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(nonClosingLedger.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(lowerBoundLedger.contains(" and effective_date >= ?"));
    assertFalse(lowerBoundLedger.contains("effective_date <= ?"));
    assertFalse(upperBoundLedger.contains("effective_date >= ?"));
    assertTrue(upperBoundLedger.contains(" and effective_date <= ?"));
    assertTrue(boundedLedger.contains(" and effective_date >= ?"));
    assertTrue(boundedLedger.contains(" and effective_date <= ?"));
    assertTrue(cursorLedger.contains("effective_date > ?"));
    assertTrue(cursorLedger.contains("order by effective_date, recorded_at, posting_id limit ?"));
    assertTrue(priorBalanceLedger.contains("effective_date < ?"));
    assertTrue(priorBalanceLedger.contains("group by journal_line.currency_code"));
    assertTrue(unboundedNonClosingPriorBalanceLedger.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertFalse(unboundedNonClosingPriorBalanceLedger.contains("effective_date >= ?"));
    assertFalse(unboundedNonClosingPriorBalanceLedger.contains("effective_date <= ?"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqlitePostingSql.loadAccountLedgerPriorBalances(
                new AccountLedgerCriteria(
                    new AccountCode("1000"),
                    EffectiveDateRange.unbounded(),
                    PostingCoverage.ALL_POSTING_KINDS,
                    50,
                    Optional.empty())));
  }

  @Test
  void loadTrialBalanceLines_excludesClosingPostingsOnlyWhenRequested() {
    String allPostingKinds =
        SqlitePostingSql.loadTrialBalanceLines(
            new dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria(
                Optional.empty(), PostingCoverage.ALL_POSTING_KINDS, ComparativeSelection.none()));
    String nonClosingOnly =
        SqlitePostingSql.loadTrialBalanceLines(
            new dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria(
                Optional.of(LocalDate.parse("2026-04-30")),
                PostingCoverage.NON_CLOSING_POSTINGS,
                ComparativeSelection.none()));

    assertFalse(allPostingKinds.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(nonClosingOnly.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(nonClosingOnly.contains(" and posting_fact.effective_date <= ?"));
  }

  @Test
  void loadAccountTotals_includesRequestedCoverageAndDateBounds() {
    String queryWithoutEffectiveDateTo =
        SqlitePostingSql.loadAccountTotals(
            new dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria(
                Optional.empty(), PostingCoverage.ALL_POSTING_KINDS, ComparativeSelection.none()));
    String unboundedAllPostingKinds =
        SqlitePostingSql.loadAccountTotals(
            EffectiveDateRange.of(null, null), PostingCoverage.ALL_POSTING_KINDS);
    String toOnlyNonClosing =
        SqlitePostingSql.loadAccountTotals(
            new dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria(
                Optional.of(LocalDate.parse("2026-04-30")),
                PostingCoverage.NON_CLOSING_POSTINGS,
                ComparativeSelection.none()));
    String fromOnlyAllPostingKinds =
        SqlitePostingSql.loadAccountTotals(
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), null),
            PostingCoverage.ALL_POSTING_KINDS);
    String boundedAllPostingKinds =
        SqlitePostingSql.loadAccountTotals(
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS);

    assertFalse(unboundedAllPostingKinds.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertFalse(unboundedAllPostingKinds.contains("posting_fact.effective_date >= ?"));
    assertFalse(unboundedAllPostingKinds.contains("posting_fact.effective_date <= ?"));
    assertTrue(unboundedAllPostingKinds.contains("account.cash_flow_asset_classification"));
    assertFalse(queryWithoutEffectiveDateTo.contains("posting_fact.effective_date <= ?"));

    assertTrue(toOnlyNonClosing.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertFalse(toOnlyNonClosing.contains("posting_fact.effective_date >= ?"));
    assertTrue(toOnlyNonClosing.contains("posting_fact.effective_date <= ?"));

    assertTrue(fromOnlyAllPostingKinds.contains("posting_fact.effective_date >= ?"));
    assertFalse(fromOnlyAllPostingKinds.contains("posting_fact.effective_date <= ?"));

    assertTrue(boundedAllPostingKinds.contains("posting_fact.effective_date >= ?"));
    assertTrue(boundedAllPostingKinds.contains("posting_fact.effective_date <= ?"));
  }

  @Test
  void loadPeriodSummaryLines_andBalanceOrderingHonorPostingCoverageBranches() {
    String allPostingKinds =
        SqlitePostingSql.loadPeriodSummaryLines(
            new PeriodSummaryCriteria(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS));
    String nonClosingOnly =
        SqlitePostingSql.loadPeriodSummaryLines(
            new PeriodSummaryCriteria(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.NON_CLOSING_POSTINGS));
    List<CurrencyBalance> sortedBalances =
        List.of(
                CurrencyBalance.ofTotals(Money.parse("USD", "1.00"), Money.parse("USD", "0.00")),
                CurrencyBalance.ofTotals(Money.parse("EUR", "1.00"), Money.parse("EUR", "0.00")))
            .stream()
            .sorted(SqliteReportRowValues.BALANCE_ORDER)
            .toList();

    assertFalse(allPostingKinds.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(nonClosingOnly.contains(NON_CLOSING_POSTING_KIND_FILTER));
    assertTrue(allPostingKinds.contains("account.cash_flow_asset_classification"));
    assertEquals(BalanceSide.DEBIT, sortedBalances.getFirst().balanceSide());
    assertEquals("EUR", sortedBalances.getFirst().netAmount().currencyUnit().code());
    assertEquals("USD", sortedBalances.getLast().netAmount().currencyUnit().code());
  }

  @Test
  void findAccountsByCodeCount_rejectsNonPositiveCounts() {
    assertThrows(IllegalArgumentException.class, () -> SqlitePostingSql.findAccountsByCodeCount(0));
  }

  @Test
  void listAccounts_andPositiveAccountLookupCounts_delegateToTheCanonicalQueryOwner() {
    assertTrue(SqlitePostingSql.listAccounts().contains("order by account_code limit ?"));
    assertTrue(
        SqlitePostingSql.findAccountsByCodeCount(2).contains("where account_code in (?, ?)"));
  }
}
