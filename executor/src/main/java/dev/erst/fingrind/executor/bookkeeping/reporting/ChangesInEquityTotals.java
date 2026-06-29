package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityRowView;
import java.util.List;

/** Owns opening, movement, and closing total aggregation for changes-in-equity projections. */
final class ChangesInEquityTotals {
  private ChangesInEquityTotals() {}

  static List<CurrencyBalance> aggregateOpeningTotals(List<ChangesInEquityRowView> rows) {
    return ReportingBalanceSupport.aggregateBalances(
        rows.stream().map(ChangesInEquityRowView::openingBalance).toList());
  }

  static List<CurrencyBalance> aggregateMovementTotals(List<ChangesInEquityRowView> rows) {
    return ReportingBalanceSupport.aggregateBalances(
        rows.stream().map(ChangesInEquityRowView::movement).toList());
  }

  static List<CurrencyBalance> aggregateClosingTotals(List<ChangesInEquityRowView> rows) {
    return ReportingBalanceSupport.aggregateBalances(
        rows.stream().map(ChangesInEquityRowView::closingBalance).toList());
  }
}
