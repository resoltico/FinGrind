package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/** Shared statement-of-changes-in-equity table rendering. */
final class PdfChangesInEquityTableSupport {
  private PdfChangesInEquityTableSupport() {}

  static void writeChangesTable(
      PdfPageWriter pageWriter, String heading, List<ChangesInEquityRow> rows) throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.changesInEquityColumns(),
        rows.stream().map(PdfChangesInEquityTableSupport::row).toList());
  }

  static void writeTotalsTable(
      PdfPageWriter pageWriter,
      String heading,
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals)
      throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.equityTotalsColumns(),
        Stream.concat(
                openingTotals.stream().map(balance -> totalRow("Opening", balance)),
                Stream.concat(
                    movementTotals.stream().map(balance -> totalRow("Movement", balance)),
                    closingTotals.stream().map(balance -> totalRow("Closing", balance))))
            .toList());
  }

  private static List<String> row(ChangesInEquityRow row) {
    return List.of(
        PdfValueFormatter.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        PdfValueFormatter.displayLineRole(row.lineRole()),
        PdfValueFormatter.displayFinancialPositionLineClassification(row.lineClassification()),
        PdfValueFormatter.displayRowKind(row.lineKind()),
        row.closingBalance().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.openingBalance().netAmount()),
        PdfValueFormatter.displayMoney(row.movement().netAmount()),
        PdfValueFormatter.displayMoney(row.closingBalance().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.closingBalance().balanceSide()));
  }

  private static List<String> totalRow(String basis, CurrencyBalance balance) {
    return List.of(
        basis,
        balance.netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(balance.debitTotal()),
        PdfValueFormatter.displayMoney(balance.creditTotal()),
        PdfValueFormatter.displayMoney(balance.netAmount()),
        PdfValueFormatter.displayBalanceSide(balance.balanceSide()));
  }
}
