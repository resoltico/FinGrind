package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerEntryView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads running-ledger views for one declared account. */
final class SqliteAccountLedgerReader {
  private final SqlitePostingReader postingReader;

  SqliteAccountLedgerReader(SqlitePostingReader postingReader) {
    this.postingReader = Objects.requireNonNull(postingReader, "postingReader");
  }

  AccountLedgerView accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerCriteria query, RegisteredAccount account) {
    List<CurrencyBalance> openingBalances = openingBalances(activeDatabase, query, account);
    Map<CurrencyUnit, Long> runningTotals = signedRunningTotals(openingBalances);
    List<AccountLedgerEntryView> entries = new ArrayList<>();
    for (CommittedPosting posting : postingsForAccountLedger(activeDatabase, query)) {
      LedgerMovement movement = ledgerMovement(posting, account);
      long signedNet = Math.subtractExact(movement.debit, movement.credit);
      long runningSigned = runningTotals.merge(movement.currencyCode, signedNet, Math::addExact);
      entries.add(
          new AccountLedgerEntryView(
              posting,
              BalanceMath.currencyBalance(movement.currencyCode, movement.debit, movement.credit),
              Money.ofMinorUnits(
                  movement.currencyCode, BalanceMath.absoluteMinorUnits(runningSigned)),
              BalanceMath.balanceSide(runningSigned)));
    }
    return new AccountLedgerView(
        account,
        query.effectiveDateRange(),
        openingBalances,
        entries,
        closingBalances(activeDatabase, query, account));
  }

  private List<CurrencyBalance> openingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerCriteria query, RegisteredAccount account) {
    Optional<LocalDate> effectiveDateFrom = query.effectiveDateRange().effectiveDateFrom();
    if (effectiveDateFrom.isEmpty()) {
      return List.of();
    }
    LocalDate lowerBound = effectiveDateFrom.orElseThrow();
    if (lowerBound.equals(LocalDate.MIN)) {
      return List.of();
    }
    return postingReader
        .accountBalance(
            activeDatabase,
            new AccountBalanceCriteria(
                account.accountCode(), EffectiveDateRange.of(null, lowerBound.minusDays(1))),
            account)
        .balances();
  }

  private List<CurrencyBalance> closingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerCriteria query, RegisteredAccount account) {
    return postingReader
        .accountBalance(
            activeDatabase,
            new AccountBalanceCriteria(
                account.accountCode(),
                EffectiveDateRange.of(
                    null, query.effectiveDateRange().effectiveDateTo().orElse(null))),
            account)
        .balances();
  }

  private List<CommittedPosting> postingsForAccountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerCriteria query) {
    return postingReader.loadCommittedPostings(
        activeDatabase,
        SqlitePostingSql.listPostingsForAccountLedger(query),
        statement -> {
          int bindIndex = 1;
          statement.bindText(bindIndex, query.accountCode().value());
          bindIndex++;
          if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
            statement.bindText(
                bindIndex, query.effectiveDateRange().effectiveDateFrom().orElseThrow().toString());
            bindIndex++;
          }
          if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
            statement.bindText(
                bindIndex, query.effectiveDateRange().effectiveDateTo().orElseThrow().toString());
          }
        });
  }

  private static LedgerMovement ledgerMovement(
      CommittedPosting posting, RegisteredAccount account) {
    List<JournalLine> matchingLines =
        posting.journalEntry().lines().stream()
            .filter(line -> line.accountCode().equals(account.accountCode()))
            .toList();
    CurrencyUnit currencyCode = posting.journalEntry().currencyUnit();
    long debit = 0L;
    long credit = 0L;
    for (JournalLine line : matchingLines) {
      if (line.side() == JournalLine.EntrySide.DEBIT) {
        debit = Math.addExact(debit, line.amount().minorUnits());
      } else {
        credit = Math.addExact(credit, line.amount().minorUnits());
      }
    }
    return new LedgerMovement(currencyCode, debit, credit);
  }

  private static Map<CurrencyUnit, Long> signedRunningTotals(
      List<CurrencyBalance> openingBalances) {
    Map<CurrencyUnit, Long> runningTotals = SqliteReportRowValues.insertionOrderedMap();
    for (CurrencyBalance balance :
        openingBalances.stream().sorted(SqliteReportRowValues.BALANCE_ORDER).toList()) {
      long signedNet =
          balance.balanceSide() == BalanceSide.DEBIT
              ? balance.netAmount().minorUnits()
              : -balance.netAmount().minorUnits();
      runningTotals.put(balance.netAmount().currencyUnit(), signedNet);
    }
    return runningTotals;
  }

  private record LedgerMovement(CurrencyUnit currencyCode, long debit, long credit) {
    private LedgerMovement {
      Objects.requireNonNull(currencyCode, "currencyCode");
    }
  }
}
