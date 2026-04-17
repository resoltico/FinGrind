package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for query-shape builders in {@link SqlitePostingSql}. */
class SqlitePostingSqlTest {
  @Test
  void listPostings_includesOnlyRequestedFilters() {
    String unfiltered = SqlitePostingSql.listPostings(false, false, false);
    String fullyFiltered = SqlitePostingSql.listPostings(true, true, true);

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
  }

  @Test
  void loadAccountLinesForBalance_includesOnlyRequestedDateFilters() {
    String unfiltered = SqlitePostingSql.loadAccountLinesForBalance(false, false);
    String fullyFiltered = SqlitePostingSql.loadAccountLinesForBalance(true, true);

    assertFalse(unfiltered.contains("posting_fact.effective_date >= ?"));
    assertFalse(unfiltered.contains("posting_fact.effective_date <= ?"));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date >= ?"));
    assertTrue(fullyFiltered.contains(" and posting_fact.effective_date <= ?"));
  }
}
