package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.policy.DerivedEquityLine;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared ordering and balance helpers for statement projections. */
final class ReportingViewSupport {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyUnit().code());
  static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      Comparator.comparing(FinancialPositionRowView::lineKind)
          .thenComparing(
              row ->
                  row.lineClassification()
                      .map(FinancialPositionLineClassification::wireValue)
                      .orElse(""))
          .thenComparing(FinancialPositionRowView::lineCode)
          .thenComparing(row -> row.balance().netAmount().currencyUnit().code());
  static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      Comparator.comparing(IncomeStatementRowView::lineKind)
          .thenComparing(IncomeStatementRowView::lineClassification)
          .thenComparing(IncomeStatementRowView::lineCode)
          .thenComparing(row -> row.movement().netAmount().currencyUnit().code());
  static final Comparator<ChangesInEquityRowView> CHANGES_IN_EQUITY_ROW_ORDER =
      Comparator.comparing(ChangesInEquityRowView::lineKind)
          .thenComparing(
              row ->
                  row.lineClassification()
                      .map(FinancialPositionLineClassification::wireValue)
                      .orElse(""))
          .thenComparing(ChangesInEquityRowView::lineCode)
          .thenComparing(row -> row.closingBalance().netAmount().currencyUnit().code());

  private ReportingViewSupport() {}

  static FinancialPositionSectionView toFinancialPositionSection(
      AccountType accountType, List<FinancialPositionRowView> rows) {
    List<FinancialPositionRowView> orderedRows =
        rows.stream().sorted(FINANCIAL_POSITION_ROW_ORDER).toList();
    return new FinancialPositionSectionView(
        accountType,
        orderedRows,
        aggregateBalances(orderedRows.stream().map(FinancialPositionRowView::balance).toList()));
  }

  static IncomeStatementSectionView toIncomeStatementSection(
      AccountType accountType, List<IncomeStatementRowView> rows) {
    List<IncomeStatementRowView> orderedRows =
        rows.stream().sorted(INCOME_STATEMENT_ROW_ORDER).toList();
    return new IncomeStatementSectionView(
        accountType,
        orderedRows,
        aggregateBalances(orderedRows.stream().map(IncomeStatementRowView::movement).toList()));
  }

  static FinancialPositionRowView financialPositionRow(AccountCurrencyTotals accountTotal) {
    return new FinancialPositionRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountType(),
        java.util.Optional.of(accountTotal.account().accountRole()),
        accountTotal.account().accountTaxonomy().financialPositionLineClassification(),
        dev.erst.fingrind.core.StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  static FinancialPositionRowView currentEarningsFinancialPositionRow(
      DerivedEquityLine currentPeriodResultLine, CurrencyUnit currencyUnit, long signedMinorUnits) {
    return new FinancialPositionRowView(
        currentPeriodResultLine.lineCode(),
        currentPeriodResultLine.lineName(),
        AccountType.EQUITY,
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        dev.erst.fingrind.core.StatementLineKind.CURRENT_PERIOD_RESULT,
        signedBalance(currencyUnit, signedMinorUnits));
  }

  static IncomeStatementRowView incomeStatementRow(AccountCurrencyTotals accountTotal) {
    return new IncomeStatementRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountType(),
        java.util.Optional.of(accountTotal.account().accountRole()),
        accountTotal.account().accountTaxonomy().profitAndLossLineClassification().orElseThrow(),
        dev.erst.fingrind.core.StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  static List<CurrencyBalance> aggregateOpeningTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::openingBalance).toList());
  }

  static List<CurrencyBalance> aggregateMovementTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::movement).toList());
  }

  static List<CurrencyBalance> aggregateClosingTotals(List<ChangesInEquityRowView> rows) {
    return aggregateBalances(rows.stream().map(ChangesInEquityRowView::closingBalance).toList());
  }

  static List<CurrencyBalance> aggregateBalances(List<CurrencyBalance> balances) {
    Map<CurrencyUnit, SignedDebitCreditTotals> totalsByCurrency =
        balances.stream()
            .collect(
                Collectors.toConcurrentMap(
                    balance -> balance.netAmount().currencyUnit(),
                    balance ->
                        new SignedDebitCreditTotals(
                            balance.debitTotal().minorUnits(), balance.creditTotal().minorUnits()),
                    SignedDebitCreditTotals::plus));
    return totalsByCurrency.entrySet().stream()
        .map(entry -> entry.getValue().balance(entry.getKey()))
        .sorted(BALANCE_ORDER)
        .toList();
  }

  @SafeVarargs
  static List<CurrencyUnit> currencyUnits(Map<CurrencyUnit, ?>... maps) {
    SortedSet<CurrencyUnit> ordered = new TreeSet<>(Comparator.comparing(CurrencyUnit::code));
    for (Map<CurrencyUnit, ?> map : maps) {
      ordered.addAll(map.keySet());
    }
    return List.copyOf(ordered);
  }

  static CurrencyBalance balanceOrZero(
      @Nullable AccountCurrencyTotals accountTotal, CurrencyUnit currencyUnit) {
    return accountTotal == null
        ? BalanceMath.currencyBalance(currencyUnit, 0L, 0L)
        : accountTotal.balance();
  }

  static CurrencyBalance signedBalance(CurrencyUnit currencyUnit, long signedMinorUnits) {
    return signedMinorUnits >= 0L
        ? BalanceMath.currencyBalance(currencyUnit, 0L, signedMinorUnits)
        : BalanceMath.currencyBalance(currencyUnit, Math.absExact(signedMinorUnits), 0L);
  }

  static void assertAccountingEquation(List<FinancialPositionSectionView> sections) {
    Map<CurrencyUnit, Long> assetTotals = signedSectionTotals(section(sections, AccountType.ASSET));
    Map<CurrencyUnit, Long> liabilityTotals =
        signedSectionTotals(section(sections, AccountType.LIABILITY));
    Map<CurrencyUnit, Long> equityTotals =
        signedSectionTotals(section(sections, AccountType.EQUITY));
    for (CurrencyUnit currencyUnit : currencyUnits(assetTotals, liabilityTotals, equityTotals)) {
      long signedTotal =
          Math.addExact(
              assetTotals.getOrDefault(currencyUnit, 0L),
              Math.addExact(
                  liabilityTotals.getOrDefault(currencyUnit, 0L),
                  equityTotals.getOrDefault(currencyUnit, 0L)));
      if (signedTotal != 0L) {
        throw new IllegalStateException(
            "Financial position violates the accounting equation for currency "
                + currencyUnit.code()
                + ".");
      }
    }
  }

  static long signedMinorUnits(CurrencyBalance balance) {
    return switch (balance.balanceSide()) {
      case DEBIT -> balance.netAmount().minorUnits();
      case CREDIT -> Math.negateExact(balance.netAmount().minorUnits());
      case ZERO -> 0L;
    };
  }

  private static FinancialPositionSectionView section(
      List<FinancialPositionSectionView> sections, AccountType accountType) {
    return sections.stream()
        .filter(section -> section.accountType() == accountType)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing statement-of-financial-position section: " + accountType));
  }

  private static Map<CurrencyUnit, Long> signedSectionTotals(FinancialPositionSectionView section) {
    return Map.copyOf(
        section.totals().stream()
            .collect(
                Collectors.toConcurrentMap(
                    balance -> balance.netAmount().currencyUnit(),
                    ReportingViewSupport::signedMinorUnits,
                    Math::addExact)));
  }

  /** Running debit and credit totals for one currency during statement aggregation. */
  private record SignedDebitCreditTotals(long debitTotalMinor, long creditTotalMinor) {
    private SignedDebitCreditTotals plus(long debitMinor, long creditMinor) {
      return new SignedDebitCreditTotals(
          Math.addExact(debitTotalMinor, debitMinor), Math.addExact(creditTotalMinor, creditMinor));
    }

    private SignedDebitCreditTotals plus(SignedDebitCreditTotals other) {
      return plus(other.debitTotalMinor(), other.creditTotalMinor());
    }

    private CurrencyBalance balance(CurrencyUnit currencyUnit) {
      return BalanceMath.currencyBalance(currencyUnit, debitTotalMinor, creditTotalMinor);
    }
  }
}
