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
        PdfStatementMetadataRows.statementParameters(
            report.bookIdentity(),
            report.comparativeEffectiveDateRange(),
            report.postingCoverage(),
            List.of(
                List.of(
                    "Effective date to",
                    PdfValueFormatter.optionalDate(report.effectiveDateTo().orElse(null))))));
    renderSections(pageWriter, report.sections(), "");
    renderSections(pageWriter, report.comparativeSections(), "Comparative ");
  }

  private static void renderSections(
      PdfPageWriter pageWriter,
      List<dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection> sections,
      String titlePrefix)
      throws IOException {
    for (var section : sections) {
      if (section.rows().isEmpty() && section.totals().isEmpty()) {
        continue;
      }
      String sectionTitle =
          titlePrefix + PdfValueFormatter.displayAccountTypeSection(section.accountType());
      pageWriter.writeTable(
          sectionTitle,
          List.of(
              new PdfTableColumn("Line code", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Line name", 1.7f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Role", 1.0f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Classification", 1.2f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Line kind", 1.1f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Currency", 0.8f, PdfTableColumn.CellAlignment.LEFT),
              new PdfTableColumn("Debit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Credit", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Net", 0.9f, PdfTableColumn.CellAlignment.RIGHT),
              new PdfTableColumn("Side", 0.8f, PdfTableColumn.CellAlignment.LEFT)),
          section.rows().stream()
              .map(
                  row ->
                      List.of(
                          PdfValueFormatter.displayStatementLineCode(
                              row.lineCode(), row.lineKind()),
                          row.lineName(),
                          PdfValueFormatter.displayLineRole(row.lineRole()),
                          PdfValueFormatter.displayFinancialPositionLineClassification(
                              row.lineClassification()),
                          PdfValueFormatter.displayRowKind(row.lineKind()),
                          row.balance().netAmount().currencyUnit().code(),
                          PdfValueFormatter.displayMoney(row.balance().debitTotal()),
                          PdfValueFormatter.displayMoney(row.balance().creditTotal()),
                          PdfValueFormatter.displayMoney(row.balance().netAmount()),
                          PdfValueFormatter.displayBalanceSide(row.balance().balanceSide())))
              .toList());
      if (!section.totals().isEmpty()) {
        pageWriter.writeTable(
            sectionTitle + " Totals",
            balanceSummaryColumns(),
            section.totals().stream()
                .map(FinancialPositionPdfRenderer::balanceSummaryRow)
                .toList());
      }
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
