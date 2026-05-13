package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders statements of financial position as PDF documents. */
final class FinancialPositionPdfRenderer {
  void render(PdfPageWriter pageWriter, FinancialPositionReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Parameters",
        List.of(
            List.of(
                "Effective date to",
                PdfValueFormatter.optionalDate(report.effectiveDateTo().orElse(null)))));
    for (var section : report.sections()) {
      String sectionTitle = PdfValueFormatter.displayAccountTypeSection(section.accountType());
      pageWriter.writeTable(
          sectionTitle,
          List.of(
              new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Line name", 1.7f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Kind", 0.8f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
          section.rows().stream()
              .map(
                  row ->
                      List.of(
                          row.lineCode(),
                          row.lineName(),
                          PdfValueFormatter.displayRowKind(row.synthetic()),
                          row.balance().netAmount().currencyUnit().code(),
                          PdfValueFormatter.displayMoney(row.balance().debitTotal()),
                          PdfValueFormatter.displayMoney(row.balance().creditTotal()),
                          PdfValueFormatter.displayMoney(row.balance().netAmount()),
                          PdfValueFormatter.displayBalanceSide(row.balance().balanceSide())))
              .toList());
      pageWriter.writeTable(
          sectionTitle + " Totals",
          balanceSummaryColumns(),
          section.totals().stream().map(FinancialPositionPdfRenderer::balanceSummaryRow).toList());
    }
  }

  private static List<PdfTableColumn> balanceSummaryColumns() {
    return List.of(
        new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
        new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
        new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT));
  }

  private static List<String> balanceSummaryRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(balance.debitTotal()),
        PdfValueFormatter.displayMoney(balance.creditTotal()),
        PdfValueFormatter.displayMoney(balance.netAmount()),
        PdfValueFormatter.displayBalanceSide(balance.balanceSide()));
  }
}
