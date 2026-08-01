package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.CashFlowRowView;
import dev.erst.fingrind.executor.bookkeeping.CashFlowSectionView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionSectionView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementSectionView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.policy.DerivedEquityLine;
import java.util.List;

/** Owns immutable statement-row and statement-section projections for bookkeeping reporting. */
final class ReportingRowViewFactory {
  private ReportingRowViewFactory() {}

  static FinancialPositionSectionView toFinancialPositionSection(
      AccountType accountType, List<FinancialPositionRowView> rows) {
    List<FinancialPositionRowView> orderedRows =
        rows.stream().sorted(ReportingRowOrdering.FINANCIAL_POSITION_ROW_ORDER).toList();
    return new FinancialPositionSectionView(
        accountType,
        orderedRows,
        ReportingBalanceSupport.aggregateBalances(
            orderedRows.stream().map(FinancialPositionRowView::balance).toList()));
  }

  static IncomeStatementSectionView toIncomeStatementSection(
      AccountType accountType, List<IncomeStatementRowView> rows) {
    List<IncomeStatementRowView> orderedRows =
        rows.stream().sorted(ReportingRowOrdering.INCOME_STATEMENT_ROW_ORDER).toList();
    return new IncomeStatementSectionView(
        accountType,
        orderedRows,
        ReportingBalanceSupport.aggregateBalances(
            orderedRows.stream().map(IncomeStatementRowView::movement).toList()));
  }

  static CashFlowSectionView toCashFlowSection(
      CashFlowSectionKind sectionKind, List<CashFlowRowView> rows) {
    List<CashFlowRowView> orderedRows =
        rows.stream().sorted(ReportingRowOrdering.CASH_FLOW_ROW_ORDER).toList();
    return new CashFlowSectionView(
        sectionKind,
        orderedRows,
        ReportingBalanceSupport.aggregateBalances(
            orderedRows.stream().map(CashFlowRowView::movement).toList()));
  }

  static FinancialPositionRowView financialPositionRow(AccountCurrencyTotals accountTotal) {
    return new FinancialPositionRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountTaxonomy().contraOfAccountCode().map(value -> value.value()),
        accountTotal.account().accountType(),
        accountTotal.account().accountTaxonomy().financialPositionLineClassification(),
        dev.erst.fingrind.core.StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  static FinancialPositionRowView currentEarningsFinancialPositionRow(
      DerivedEquityLine currentPeriodResultLine, CurrencyUnit currencyUnit, long signedMinorUnits) {
    return new FinancialPositionRowView(
        currentPeriodResultLine.lineCode(),
        currentPeriodResultLine.lineName(),
        java.util.Optional.empty(),
        AccountType.EQUITY,
        java.util.Optional.empty(),
        dev.erst.fingrind.core.StatementLineKind.CURRENT_PERIOD_RESULT,
        ReportingBalanceSupport.signedBalance(currencyUnit, signedMinorUnits));
  }

  static IncomeStatementRowView incomeStatementRow(AccountCurrencyTotals accountTotal) {
    return new IncomeStatementRowView(
        accountTotal.account().accountCode().value(),
        accountTotal.account().accountName().value(),
        accountTotal.account().accountTaxonomy().contraOfAccountCode().map(value -> value.value()),
        accountTotal.account().accountType(),
        accountTotal.account().accountTaxonomy().profitAndLossLineClassification().orElseThrow(),
        dev.erst.fingrind.core.StatementLineKind.DECLARED_ACCOUNT,
        accountTotal.balance());
  }

  static CashFlowRowView cashFlowRow(RegisteredAccount account, CurrencyBalance movement) {
    return new CashFlowRowView(
        account.accountCode().value(),
        account.accountName().value(),
        account.accountType(),
        account.accountTaxonomy().financialPositionLineClassification(),
        account.accountTaxonomy().profitAndLossLineClassification(),
        dev.erst.fingrind.core.StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }
}
