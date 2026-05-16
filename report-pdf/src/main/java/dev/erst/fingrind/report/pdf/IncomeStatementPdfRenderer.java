package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders income statements as PDF documents. */
final class IncomeStatementPdfRenderer {
  void render(PdfPageWriter pageWriter, IncomeStatementReport report) throws IOException {
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
    renderSections(pageWriter, report.sections(), "");
    pageWriter.writeTable(
        "Net Income Totals",
        balanceSummaryColumns(),
        report.netIncomeTotals().stream()
            .map(IncomeStatementPdfRenderer::balanceSummaryRow)
            .toList());
    renderSections(pageWriter, report.comparativeSections(), "Comparative ");
    if (!report.comparativeNetIncomeTotals().isEmpty()) {
      pageWriter.writeTable(
          "Comparative Net Income Totals",
          balanceSummaryColumns(),
          report.comparativeNetIncomeTotals().stream()
              .map(IncomeStatementPdfRenderer::balanceSummaryRow)
              .toList());
    }
  }

  private static void renderSections(
      PdfPageWriter pageWriter,
      List<dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection> sections,
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
                          row.lineCode(),
                          row.lineName(),
                          PdfValueFormatter.displayLineRole(row.lineRole()),
                          PdfValueFormatter.displayProfitAndLossLineClassification(
                              row.lineClassification()),
                          PdfValueFormatter.displayRowKind(row.lineKind()),
                          row.movement().netAmount().currencyUnit().code(),
                          PdfValueFormatter.displayMoney(row.movement().debitTotal()),
                          PdfValueFormatter.displayMoney(row.movement().creditTotal()),
                          PdfValueFormatter.displayMoney(row.movement().netAmount()),
                          PdfValueFormatter.displayBalanceSide(row.movement().balanceSide())))
              .toList());
      if (!section.totals().isEmpty()) {
        pageWriter.writeTable(
            sectionTitle + " Totals",
            balanceSummaryColumns(),
            section.totals().stream().map(IncomeStatementPdfRenderer::balanceSummaryRow).toList());
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
