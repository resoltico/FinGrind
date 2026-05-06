package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerEntryView;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.math.BigDecimal;
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
    Map<CurrencyCode, BigDecimal> runningTotals = signedRunningTotals(openingBalances);
    List<AccountLedgerEntryView> entries = new ArrayList<>();
    for (CommittedPosting posting : postingsForAccountLedger(activeDatabase, query)) {
      LedgerMovement movement = ledgerMovement(posting, account);
      BigDecimal signedNet = movement.debit.subtract(movement.credit);
      BigDecimal runningSigned =
          runningTotals.merge(movement.currencyCode, signedNet, BigDecimal::add);
      entries.add(
          new AccountLedgerEntryView(
              posting,
              SqliteBalanceMath.currencyBalance(
                  movement.currencyCode, movement.debit, movement.credit),
              new Money(movement.currencyCode, runningSigned.abs()),
              runningBalanceSide(runningSigned)));
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
    CurrencyCode currencyCode = matchingLines.getFirst().amount().currencyCode();
    BigDecimal debit = BigDecimal.ZERO;
    BigDecimal credit = BigDecimal.ZERO;
    for (JournalLine line : matchingLines) {
      if (line.side() == JournalLine.EntrySide.DEBIT) {
        debit = debit.add(line.amount().amount());
      } else {
        credit = credit.add(line.amount().amount());
      }
    }
    return new LedgerMovement(currencyCode, debit, credit);
  }

  private static Map<CurrencyCode, BigDecimal> signedRunningTotals(
      List<CurrencyBalance> openingBalances) {
    Map<CurrencyCode, BigDecimal> runningTotals = SqliteReportRowValues.insertionOrderedMap();
    for (CurrencyBalance balance :
        openingBalances.stream().sorted(SqliteReportRowValues.BALANCE_ORDER).toList()) {
      BigDecimal signedNet =
          balance.balanceSide() == BalanceSide.DEBIT
              ? balance.netAmount().amount()
              : balance.netAmount().amount().negate();
      runningTotals.put(balance.netAmount().currencyCode(), signedNet);
    }
    return runningTotals;
  }

  private static BalanceSide runningBalanceSide(BigDecimal signedBalance) {
    if (signedBalance.signum() == 0) {
      return BalanceSide.ZERO;
    }
    return signedBalance.signum() > 0 ? BalanceSide.DEBIT : BalanceSide.CREDIT;
  }

  private record LedgerMovement(CurrencyCode currencyCode, BigDecimal debit, BigDecimal credit) {
    private LedgerMovement {
      Objects.requireNonNull(currencyCode, "currencyCode");
      Objects.requireNonNull(debit, "debit");
      Objects.requireNonNull(credit, "credit");
    }
  }
}
