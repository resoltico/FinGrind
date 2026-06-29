package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import java.util.List;

/** Shared statement row renderers for balance-style financial statement tables. */
final class PdfStatementRowRenderers {
  private PdfStatementRowRenderers() {}

  static List<String> financialPositionRow(FinancialPositionRow row) {
    return List.of(
        PdfValueFormatter.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        PdfValueFormatter.displayFinancialPositionLineClassification(row.lineClassification()),
        PdfValueFormatter.displayRowKind(row.lineKind()),
        row.balance().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.balance().debitTotal()),
        PdfValueFormatter.displayMoney(row.balance().creditTotal()),
        PdfValueFormatter.displayMoney(row.balance().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.balance().balanceSide()));
  }

  static List<String> incomeStatementRow(IncomeStatementRow row) {
    return List.of(
        PdfValueFormatter.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        PdfValueFormatter.displayProfitAndLossLineClassification(row.lineClassification()),
        PdfValueFormatter.displayRowKind(row.lineKind()),
        row.movement().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.movement().debitTotal()),
        PdfValueFormatter.displayMoney(row.movement().creditTotal()),
        PdfValueFormatter.displayMoney(row.movement().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.movement().balanceSide()));
  }

  static List<String> cashFlowRow(CashFlowRow row) {
    return List.of(
        PdfValueFormatter.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        row.financialPositionLineClassification()
            .map(PdfValueFormatter::displayFinancialPositionLineClassification)
            .or(
                () ->
                    row.profitAndLossLineClassification()
                        .map(PdfValueFormatter::displayProfitAndLossLineClassification))
            .orElse("Calculated line"),
        PdfValueFormatter.displayRowKind(row.lineKind()),
        row.movement().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.movement().debitTotal()),
        PdfValueFormatter.displayMoney(row.movement().creditTotal()),
        PdfValueFormatter.displayMoney(row.movement().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.movement().balanceSide()));
  }
}
