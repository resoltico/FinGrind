package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
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
    PdfChangesInEquityTableSupport.writeChangesTable(
        pageWriter, "Changes In Equity", report.rows());
    PdfChangesInEquityTableSupport.writeTotalsTable(
        pageWriter,
        "Equity Totals",
        report.openingTotals(),
        report.movementTotals(),
        report.closingTotals());
    if (!report.comparativeRows().isEmpty()) {
      PdfChangesInEquityTableSupport.writeChangesTable(
          pageWriter, "Comparative Changes In Equity", report.comparativeRows());
    }
    if (!report.comparativeOpeningTotals().isEmpty()
        || !report.comparativeMovementTotals().isEmpty()
        || !report.comparativeClosingTotals().isEmpty()) {
      PdfChangesInEquityTableSupport.writeTotalsTable(
          pageWriter,
          "Comparative Equity Totals",
          report.comparativeOpeningTotals(),
          report.comparativeMovementTotals(),
          report.comparativeClosingTotals());
    }
  }
}
