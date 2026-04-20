package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads running-ledger views for one declared account. */
final class SqliteAccountLedgerReadSupport {
  private final SqlitePostingReadSupport postingReadSupport;

  SqliteAccountLedgerReadSupport(SqlitePostingReadSupport postingReadSupport) {
    this.postingReadSupport = Objects.requireNonNull(postingReadSupport, "postingReadSupport");
  }

  AccountLedgerReport accountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    List<CurrencyBalance> openingBalances = openingBalances(activeDatabase, query, account);
    Map<CurrencyCode, BigDecimal> runningTotals = signedRunningTotals(openingBalances);
    List<AccountLedgerEntry> entries = new ArrayList<>();
    for (PostingFact postingFact : postingFactsForAccountLedger(activeDatabase, query)) {
      LedgerMovement movement = ledgerMovement(postingFact, account);
      BigDecimal signedNet = movement.debit.subtract(movement.credit);
      BigDecimal runningSigned =
          runningTotals.merge(movement.currencyCode, signedNet, BigDecimal::add);
      entries.add(
          new AccountLedgerEntry(
              postingFact,
              SqliteBalanceMath.currencyBalance(
                  movement.currencyCode, movement.debit, movement.credit, account.normalBalance()),
              new Money(movement.currencyCode, runningSigned.abs()),
              runningBalanceSide(runningSigned)));
    }
    return new AccountLedgerReport(
        account,
        query.effectiveDateRange(),
        openingBalances,
        entries,
        closingBalances(activeDatabase, query, account));
  }

  private List<CurrencyBalance> openingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    Optional<LocalDate> effectiveDateFrom = query.effectiveDateFrom();
    if (effectiveDateFrom.isEmpty()) {
      return List.of();
    }
    LocalDate lowerBound = effectiveDateFrom.orElseThrow();
    if (lowerBound.equals(LocalDate.MIN)) {
      return List.of();
    }
    return postingReadSupport
        .accountBalance(
            activeDatabase,
            new AccountBalanceQuery(
                account.accountCode(), Optional.empty(), Optional.of(lowerBound.minusDays(1))),
            account)
        .balances();
  }

  private List<CurrencyBalance> closingBalances(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query, DeclaredAccount account)
      throws SqliteNativeException {
    return postingReadSupport
        .accountBalance(
            activeDatabase,
            new AccountBalanceQuery(
                account.accountCode(), Optional.empty(), query.effectiveDateTo()),
            account)
        .balances();
  }

  private List<PostingFact> postingFactsForAccountLedger(
      SqliteNativeDatabase activeDatabase, AccountLedgerQuery query) throws SqliteNativeException {
    boolean filterEffectiveDateFrom = query.effectiveDateFrom().isPresent();
    boolean filterEffectiveDateTo = query.effectiveDateTo().isPresent();
    return postingReadSupport.loadPostingFacts(
        activeDatabase,
        SqlitePostingSql.listPostingsForAccountLedger(
            filterEffectiveDateFrom, filterEffectiveDateTo),
        statement -> {
          int bindIndex = 1;
          statement.bindText(bindIndex, query.accountCode().value());
          bindIndex++;
          if (filterEffectiveDateFrom) {
            statement.bindText(bindIndex, query.effectiveDateFrom().orElseThrow().toString());
            bindIndex++;
          }
          if (filterEffectiveDateTo) {
            statement.bindText(bindIndex, query.effectiveDateTo().orElseThrow().toString());
          }
        });
  }

  private static LedgerMovement ledgerMovement(PostingFact postingFact, DeclaredAccount account) {
    List<JournalLine> matchingLines =
        postingFact.journalEntry().lines().stream()
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
    Map<CurrencyCode, BigDecimal> runningTotals = SqliteReportDataSupport.insertionOrderedMap();
    for (CurrencyBalance balance :
        openingBalances.stream().sorted(SqliteReportDataSupport.BALANCE_ORDER).toList()) {
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
