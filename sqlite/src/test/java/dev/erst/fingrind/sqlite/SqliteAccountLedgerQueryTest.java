package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.JournalLine;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteAccountLedgerQueryTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void accountLedger_computesOpeningRunningAndClosingBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-report.sqlite");
    PostingFact postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    PostingFact postingThree =
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
      postingFactStore.commit(postingOne);
      postingFactStore.commit(postingTwo);
      postingFactStore.commit(postingThree);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-09")),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new AccountLedgerEntry(
                      postingTwo,
                      new CurrencyBalance(
                          money("EUR", "0.00"),
                          money("EUR", "4.00"),
                          money("EUR", "4.00"),
                          BalanceSide.CREDIT),
                      money("EUR", "6.00"),
                      BalanceSide.DEBIT),
                  new AccountLedgerEntry(
                      postingThree,
                      new CurrencyBalance(
                          money("USD", "8.00"),
                          money("USD", "0.00"),
                          money("USD", "8.00"),
                          BalanceSide.DEBIT),
                      money("USD", "8.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "4.00"),
                      money("EUR", "6.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "8.00"),
                      money("USD", "0.00"),
                      money("USD", "8.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("1000"),
                  LocalDate.parse("2026-04-08"),
                  LocalDate.parse("2026-04-09")),
              cashAccount));
    }
  }

  @Test
  void accountLedger_allowsMinimumLowerBoundWithoutOpeningBalanceLookback() {
    Path databasePath = tempDirectory.resolve("account-ledger-min-lower-bound.sqlite");
    PostingFact posting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(posting);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(LocalDate.MIN, null),
              List.of(),
              List.of(
                  new AccountLedgerEntry(
                      posting,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          BalanceSide.DEBIT),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(new AccountCode("1000"), LocalDate.MIN, null), cashAccount));
    }
  }

  @Test
  void accountLedger_supportsCreditOpeningBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-credit-opening.sqlite");
    PostingFact openingPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact inRangePosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "10.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(openingPosting);
      postingFactStore.commit(inRangePosting);

      DeclaredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              revenueAccount,
              EffectiveDateRange.of(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-08")),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      money("EUR", "10.00"),
                      BalanceSide.CREDIT)),
              List.of(
                  new AccountLedgerEntry(
                      inRangePosting,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          BalanceSide.DEBIT),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("2000"),
                  LocalDate.parse("2026-04-08"),
                  LocalDate.parse("2026-04-08")),
              revenueAccount));
    }
  }

  @Test
  void accountLedger_sortsMultipleOpeningCurrenciesBeforeRunningBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-multi-opening.sqlite");
    PostingFact eurOpeningPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact usdOpeningPosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "7.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(eurOpeningPosting);
      postingFactStore.commit(usdOpeningPosting);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(LocalDate.parse("2026-04-09"), LocalDate.parse("2026-04-09")),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "7.00"),
                      money("USD", "0.00"),
                      money("USD", "7.00"),
                      BalanceSide.DEBIT)),
              List.of(),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "7.00"),
                      money("USD", "0.00"),
                      money("USD", "7.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("1000"),
                  LocalDate.parse("2026-04-09"),
                  LocalDate.parse("2026-04-09")),
              cashAccount));
    }
  }
}
