package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBalanceAndSummaryQueryTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void accountBalance_validatesBookStateAndComputesCurrencyBuckets() throws Exception {
    Path missingBookPath = tempDirectory.resolve("account-balance-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceCriteria(new AccountCode("1000"), null, null)));
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }
    Path blankBookPath = tempDirectory.resolve("account-balance-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceCriteria(new AccountCode("1000"), null, null)));
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }
    Path databasePath = tempDirectory.resolve("account-balance.sqlite");
    CommittedPosting postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    CommittedPosting postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "8.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "8.00")));
    CommittedPosting postingFour =
        postingFact(
            "posting-4",
            "idem-4",
            LocalDate.parse("2026-04-10"),
            Instant.parse("2026-04-10T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "7.00")));
    CommittedPosting postingFive =
        postingFact(
            "posting-5",
            "idem-5",
            LocalDate.parse("2026-04-11"),
            Instant.parse("2026-04-11T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "USD", "7.00")));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, postingOne);
      commitPosting(postingFactStore, postingTwo);
      commitPosting(postingFactStore, postingThree);
      commitPosting(postingFactStore, postingFour);
      commitPosting(postingFactStore, postingFive);
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      assertEquals(
          Optional.empty(),
          postingFactStore.accountBalance(
              new AccountBalanceCriteria(new AccountCode("9999"), null, null)));
      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  publishedAccount(cashAccount),
                  Optional.empty(),
                  Optional.empty(),
                  List.of(
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "12.00"),
                          money("EUR", "2.00"),
                          BalanceSide.CREDIT),
                      new CurrencyBalance(
                          money("USD", "7.00"),
                          money("USD", "7.00"),
                          money("USD", "0.00"),
                          BalanceSide.ZERO)))),
          postingFactStore
              .accountBalance(new AccountBalanceCriteria(new AccountCode("1000"), null, null))
              .map(SqliteStoreTestIntrospectionSupport::published));
      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  publishedAccount(cashAccount),
                  Optional.empty(),
                  Optional.of(LocalDate.parse("2026-04-08")),
                  List.of(
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)))),
          postingFactStore
              .accountBalance(
                  new AccountBalanceCriteria(
                      new AccountCode("1000"), null, LocalDate.parse("2026-04-08")))
              .map(SqliteStoreTestIntrospectionSupport::published));
      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  publishedAccount(cashAccount),
                  Optional.of(LocalDate.parse("2026-04-10")),
                  Optional.of(LocalDate.parse("2026-04-11")),
                  List.of(
                      new CurrencyBalance(
                          money("USD", "7.00"),
                          money("USD", "7.00"),
                          money("USD", "0.00"),
                          BalanceSide.ZERO)))),
          postingFactStore
              .accountBalance(
                  new AccountBalanceCriteria(
                      new AccountCode("1000"),
                      LocalDate.parse("2026-04-10"),
                      LocalDate.parse("2026-04-11")))
              .map(SqliteStoreTestIntrospectionSupport::published));
    }
  }

  @Test
  void trialBalance_andPeriodSummary_computeOfficeReportModels() {
    Path databasePath = tempDirectory.resolve("office-reports.sqlite");
    CommittedPosting postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    CommittedPosting postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "8.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "8.00")));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, postingOne);
      commitPosting(postingFactStore, postingTwo);
      commitPosting(postingFactStore, postingThree);
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      assertEquals(
          new TrialBalanceReport(
              Optional.of(LocalDate.parse("2026-04-08")),
              List.of(
                  new TrialBalanceRow(
                      publishedAccount(cashAccount),
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)),
                  new TrialBalanceRow(
                      publishedAccount(revenueAccount),
                      new CurrencyBalance(
                          money("EUR", "4.00"),
                          money("EUR", "10.00"),
                          money("EUR", "6.00"),
                          BalanceSide.CREDIT)))),
          published(
              postingFactStore.trialBalance(
                  new TrialBalanceCriteria(Optional.of(LocalDate.parse("2026-04-08"))))));
      assertEquals(
          new PeriodSummaryReport(
              LocalDate.parse("2026-04-07"),
              LocalDate.parse("2026-04-08"),
              2,
              4,
              2,
              List.of(
                  new PeriodCurrencySummary(
                      new CurrencyBalance(
                          money("EUR", "14.00"),
                          money("EUR", "14.00"),
                          money("EUR", "0.00"),
                          BalanceSide.ZERO))),
              List.of(
                  new PeriodAccountActivityRow(
                      publishedAccount(cashAccount),
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)),
                  new PeriodAccountActivityRow(
                      publishedAccount(revenueAccount),
                      new CurrencyBalance(
                          money("EUR", "4.00"),
                          money("EUR", "10.00"),
                          money("EUR", "6.00"),
                          BalanceSide.CREDIT)))),
          published(
              postingFactStore.periodSummary(
                  new PeriodSummaryCriteria(
                      LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08")))));
    }
  }
}
