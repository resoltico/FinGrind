package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteAccountLedgerQueryTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void accountLedger_computesOpeningRunningAndClosingBalancesInsideBookFunctionalCurrency() {
    Path databasePath = tempDirectory.resolve("account-ledger-report.sqlite");
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
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "8.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "8.00")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, postingOne);
      commitPosting(postingFactStore, postingTwo);
      commitPosting(postingFactStore, postingThree);
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      assertEquals(
          new AccountLedgerReport(
              bookIdentity(),
              publishedAccount(cashAccount),
              EffectiveDateRange.of(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-09")),
              dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
              List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
              List.of(
                  new AccountLedgerEntry(
                      publishedPostingFact(postingTwo),
                      balance("EUR", "0.00", "4.00", "4.00", BalanceSide.CREDIT),
                      money("EUR", "6.00"),
                      BalanceSide.DEBIT),
                  new AccountLedgerEntry(
                      publishedPostingFact(postingThree),
                      balance("EUR", "8.00", "0.00", "8.00", BalanceSide.DEBIT),
                      money("EUR", "14.00"),
                      BalanceSide.DEBIT)),
              List.of(balance("EUR", "18.00", "4.00", "14.00", BalanceSide.DEBIT))),
          published(
              postingFactStore.accountLedger(
                  new AccountLedgerCriteria(
                      new AccountCode("1000"),
                      LocalDate.parse("2026-04-08"),
                      LocalDate.parse("2026-04-09")),
                  cashAccount)));
    }
  }

  @Test
  void accountLedger_allowsMinimumLowerBoundWithoutOpeningBalanceLookback() {
    Path databasePath = tempDirectory.resolve("account-ledger-min-lower-bound.sqlite");
    CommittedPosting posting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, posting);
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      assertEquals(
          new AccountLedgerReport(
              bookIdentity(),
              publishedAccount(cashAccount),
              EffectiveDateRange.of(LocalDate.MIN, null),
              dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
              List.of(),
              List.of(
                  new AccountLedgerEntry(
                      publishedPostingFact(posting),
                      balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT)),
              List.of(balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT))),
          published(
              postingFactStore.accountLedger(
                  new AccountLedgerCriteria(new AccountCode("1000"), LocalDate.MIN, null),
                  cashAccount)));
    }
  }

  @Test
  void accountLedger_supportsCreditOpeningBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-credit-opening.sqlite");
    CommittedPosting openingPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting inRangePosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "10.00")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, openingPosting);
      commitPosting(postingFactStore, inRangePosting);
      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      assertEquals(
          new AccountLedgerReport(
              bookIdentity(),
              publishedAccount(revenueAccount),
              EffectiveDateRange.of(LocalDate.parse("2026-04-08"), LocalDate.parse("2026-04-08")),
              dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
              List.of(balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT)),
              List.of(
                  new AccountLedgerEntry(
                      publishedPostingFact(inRangePosting),
                      balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO)),
              List.of(balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
          published(
              postingFactStore.accountLedger(
                  new AccountLedgerCriteria(
                      new AccountCode("2000"),
                      LocalDate.parse("2026-04-08"),
                      LocalDate.parse("2026-04-08")),
                  revenueAccount)));
    }
  }

  @Test
  void accountLedger_sortsMultipleOpeningBalancesInsideBookFunctionalCurrency() {
    Path databasePath = tempDirectory.resolve("account-ledger-multi-opening.sqlite");
    CommittedPosting eurOpeningPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting secondOpeningPosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "7.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "7.00")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, eurOpeningPosting);
      commitPosting(postingFactStore, secondOpeningPosting);
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      assertEquals(
          new AccountLedgerReport(
              bookIdentity(),
              publishedAccount(cashAccount),
              EffectiveDateRange.of(LocalDate.parse("2026-04-09"), LocalDate.parse("2026-04-09")),
              dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
              List.of(balance("EUR", "17.00", "0.00", "17.00", BalanceSide.DEBIT)),
              List.of(),
              List.of(balance("EUR", "17.00", "0.00", "17.00", BalanceSide.DEBIT))),
          published(
              postingFactStore.accountLedger(
                  new AccountLedgerCriteria(
                      new AccountCode("1000"),
                      LocalDate.parse("2026-04-09"),
                      LocalDate.parse("2026-04-09")),
                  cashAccount)));
    }
  }

  private static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }
}
