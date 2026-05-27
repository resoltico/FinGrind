package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
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
    PdfStatementSectionTableRenderer.renderSections(
        pageWriter,
        report.sections(),
        "",
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::accountType,
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::rows,
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::totals,
        PdfStatementRowRenderers::incomeStatementRow);
    PdfBalanceTableSupport.writeSummaryTable(
        pageWriter, "Net Income Totals", report.netIncomeTotals());
    PdfStatementSectionTableRenderer.renderSections(
        pageWriter,
        report.comparativeSections(),
        "Comparative ",
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::accountType,
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::rows,
        dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection::totals,
        PdfStatementRowRenderers::incomeStatementRow);
    if (!report.comparativeNetIncomeTotals().isEmpty()) {
      PdfBalanceTableSupport.writeSummaryTable(
          pageWriter, "Comparative Net Income Totals", report.comparativeNetIncomeTotals());
    }
  }
}
