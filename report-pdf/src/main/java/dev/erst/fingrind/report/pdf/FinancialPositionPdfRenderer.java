package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders statements of financial position as PDF documents. */
final class FinancialPositionPdfRenderer {
  void render(PdfPageWriter pageWriter, FinancialPositionReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    PdfStatementSectionTableRenderer.renderSections(
        pageWriter,
        report.sections(),
        "",
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::accountType,
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::rows,
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::totals,
        PdfStatementRowRenderers::financialPositionRow);
    PdfStatementSectionTableRenderer.renderSections(
        pageWriter,
        report.comparativeSections(),
        "Comparative ",
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::accountType,
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::rows,
        dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection::totals,
        PdfStatementRowRenderers::financialPositionRow);
    pageWriter.writeKeyValueTable(
        "Context",
        PdfStatementMetadataRows.statementParameters(
            report.bookIdentity(),
            report.comparativeEffectiveDateRange(),
            report.postingCoverage(),
            List.of(
                List.of(
                    "Effective date as of",
                    PdfTemporalValueFormatter.optionalDate(
                        report.effectiveDateAsOf().orElse(null))),
                List.of(
                    "Accounting equation",
                    report.accountingEquationBalanced() ? "Balanced" : "Imbalanced"),
                List.of(
                    "Comparative range",
                    PdfTemporalValueFormatter.comparativeRange(
                        report.comparativeEffectiveDateRange())))));
  }
}
