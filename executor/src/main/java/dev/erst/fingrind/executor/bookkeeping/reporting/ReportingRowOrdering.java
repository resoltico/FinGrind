package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.CashFlowRowView;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionRowView;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementRowView;
import java.util.Comparator;

/** Shared row and balance ordering rules for bookkeeping statement projections. */
final class ReportingRowOrdering {
  static final Comparator<CurrencyBalance> BALANCE_ORDER =
      Comparator.comparing(balance -> balance.netAmount().currencyUnit().code());
  static final Comparator<FinancialPositionRowView> FINANCIAL_POSITION_ROW_ORDER =
      Comparator.comparing(FinancialPositionRowView::lineKind)
          .thenComparing(
              row ->
                  row.lineClassification()
                      .map(FinancialPositionLineClassification::wireValue)
                      .orElse(""))
          .thenComparing(row -> row.contraOfLineCode().orElse(row.lineCode()))
          .thenComparing(row -> row.contraOfLineCode().isPresent())
          .thenComparing(FinancialPositionRowView::lineCode)
          .thenComparing(row -> row.balance().netAmount().currencyUnit().code());
  static final Comparator<IncomeStatementRowView> INCOME_STATEMENT_ROW_ORDER =
      Comparator.comparing(IncomeStatementRowView::lineKind)
          .thenComparing(IncomeStatementRowView::lineClassification)
          .thenComparing(row -> row.contraOfLineCode().orElse(row.lineCode()))
          .thenComparing(row -> row.contraOfLineCode().isPresent())
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
  static final Comparator<CashFlowRowView> CASH_FLOW_ROW_ORDER =
      Comparator.comparing(CashFlowRowView::lineKind)
          .thenComparing(CashFlowRowView::lineCode)
          .thenComparing(row -> row.movement().netAmount().currencyUnit().code());

  private ReportingRowOrdering() {}
}
