package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders statements of changes in equity as PDF documents. */
final class ChangesInEquityPdfRenderer {
  void render(PdfPageWriter pageWriter, ChangesInEquityReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Parameters",
        PdfStatementMetadataRows.statementParameters(
            report.bookIdentity(),
            report.comparativeEffectiveDateRange(),
            report.postingCoverage(),
            List.of(
                List.of("Effective date from", report.effectiveDateFrom().toString()),
                List.of("Effective date to", report.effectiveDateTo().toString()))));
    pageWriter.writeTable(
        "Changes In Equity",
        List.of(
            new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Line name", 1.5f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Classification", 1.2f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Line kind", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Opening", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Movement", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Closing", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Closing side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
        report.rows().stream()
            .map(
                row ->
                    List.of(
                        row.lineCode(),
                        row.lineName(),
                        PdfValueFormatter.displayLineRole(row.lineRole()),
                        PdfValueFormatter.displayFinancialPositionLineClassification(
                            row.lineClassification()),
                        PdfValueFormatter.displayRowKind(row.lineKind()),
                        row.closingBalance().netAmount().currencyUnit().code(),
                        PdfValueFormatter.displayMoney(row.openingBalance().netAmount()),
                        PdfValueFormatter.displayMoney(row.movement().netAmount()),
                        PdfValueFormatter.displayMoney(row.closingBalance().netAmount()),
                        PdfValueFormatter.displayBalanceSide(row.closingBalance().balanceSide())))
            .toList());
    pageWriter.writeTable(
        "Equity Totals",
        List.of(
            new PdfTableColumn("Basis", 1.0f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
            new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
            new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
        java.util.stream.Stream.concat(
                report.openingTotals().stream().map(balance -> totalRow("Opening", balance)),
                java.util.stream.Stream.concat(
                    report.movementTotals().stream().map(balance -> totalRow("Movement", balance)),
                    report.closingTotals().stream().map(balance -> totalRow("Closing", balance))))
            .toList());
    if (!report.comparativeRows().isEmpty()) {
      pageWriter.writeTable(
          "Comparative Changes In Equity",
          List.of(
              new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Line name", 1.5f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Classification", 1.2f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Line kind", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Opening", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Movement", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Closing", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Closing side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
          report.comparativeRows().stream()
              .map(
                  row ->
                      List.of(
                          row.lineCode(),
                          row.lineName(),
                          PdfValueFormatter.displayLineRole(row.lineRole()),
                          PdfValueFormatter.displayFinancialPositionLineClassification(
                              row.lineClassification()),
                          PdfValueFormatter.displayRowKind(row.lineKind()),
                          row.closingBalance().netAmount().currencyUnit().code(),
                          PdfValueFormatter.displayMoney(row.openingBalance().netAmount()),
                          PdfValueFormatter.displayMoney(row.movement().netAmount()),
                          PdfValueFormatter.displayMoney(row.closingBalance().netAmount()),
                          PdfValueFormatter.displayBalanceSide(row.closingBalance().balanceSide())))
              .toList());
    }
    if (!report.comparativeOpeningTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingTotals().isEmpty()) {
      pageWriter.writeTable(
          "Comparative Equity Totals",
          List.of(
              new PdfTableColumn("Basis", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
          java.util.stream.Stream.concat(
                  report.comparativeOpeningTotals().stream()
                      .map(balance -> totalRow("Opening", balance)),
                  java.util.stream.Stream.concat(
                      report.comparativeMovementTotals().stream()
                          .map(balance -> totalRow("Movement", balance)),
                      report.comparativeClosingTotals().stream()
                          .map(balance -> totalRow("Closing", balance))))
              .toList());
    }
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
