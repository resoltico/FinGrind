package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for query-shape builders in {@link SqlitePostingSql}. */
class SqlitePostingSqlTest {
  @Test
  void listPostings_includesOnlyRequestedFilters() {
    String unfiltered =
        SqlitePostingSql.listPostings(
            new ListPostingsQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), 50, Optional.empty()));
    String fullyFiltered =
        SqlitePostingSql.listPostings(
            new ListPostingsQuery(
                Optional.of(new AccountCode("1000")),
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                50,
                Optional.of(
                    new PostingPageCursor(
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
            new AccountBalanceQuery(new AccountCode("1000"), Optional.empty(), Optional.empty()));
    String fullyFiltered =
        SqlitePostingSql.loadAccountLinesForBalance(
            new AccountBalanceQuery(
                new AccountCode("1000"),
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30"))));

    assertFalse(unfiltered.contains("posting_fact.effective_date >= ?"));
    assertFalse(unfiltered.contains("posting_fact.effective_date <= ?"));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date >= ?"));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date <= ?"));
  }

  @Test
  void loadTrialBalanceLines_andAccountLedgerQueries_includeOnlyRequestedFilters() {
    String unfilteredTrialBalance =
        SqlitePostingSql.loadTrialBalanceLines(new TrialBalanceQuery(Optional.empty()));
    String filteredTrialBalance =
        SqlitePostingSql.loadTrialBalanceLines(
            new TrialBalanceQuery(Optional.of(LocalDate.parse("2026-04-30"))));
    String unboundedLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerQuery(new AccountCode("1000"), Optional.empty(), Optional.empty()));
    String lowerBoundLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerQuery(
                new AccountCode("1000"),
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.empty()));
    String upperBoundLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerQuery(
                new AccountCode("1000"),
                Optional.empty(),
                Optional.of(LocalDate.parse("2026-04-30"))));
    String boundedLedger =
        SqlitePostingSql.listPostingsForAccountLedger(
            new AccountLedgerQuery(
                new AccountCode("1000"),
                Optional.of(LocalDate.parse("2026-04-01")),
                Optional.of(LocalDate.parse("2026-04-30"))));

    assertFalse(unfilteredTrialBalance.contains("posting_fact.effective_date <= ?"));
    assertTrue(filteredTrialBalance.contains(" and posting_fact.effective_date <= ?"));

    assertFalse(unboundedLedger.contains("effective_date >= ?"));
    assertFalse(unboundedLedger.contains("effective_date <= ?"));
    assertTrue(lowerBoundLedger.contains(" and effective_date >= ?"));
    assertFalse(lowerBoundLedger.contains("effective_date <= ?"));
    assertFalse(upperBoundLedger.contains("effective_date >= ?"));
    assertTrue(upperBoundLedger.contains(" and effective_date <= ?"));
    assertTrue(boundedLedger.contains(" and effective_date >= ?"));
    assertTrue(boundedLedger.contains(" and effective_date <= ?"));
  }

  @Test
  void findAccountsByCodeCount_rejectsNonPositiveCounts() {
    assertThrows(IllegalArgumentException.class, () -> SqlitePostingSql.findAccountsByCodeCount(0));
  }
}
