package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders trial-balance reports as PDF documents. */
final class TrialBalancePdfRenderer {
  void render(PdfPageWriter pageWriter, TrialBalanceReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    PdfAccountActivityTableSupport.writeTrialBalanceTable(
        pageWriter, "Trial Balance", report.rows());
    if (!report.comparativeRows().isEmpty()) {
      PdfAccountActivityTableSupport.writeTrialBalanceTable(
          pageWriter, "Comparative Trial Balance", report.comparativeRows());
    }
    pageWriter.writeKeyValueTable(
        "Context",
        PdfStatementMetadataRows.statementParameters(
            report.bookIdentity(),
            report.comparativeEffectiveDateRange(),
            report.postingCoverage(),
            List.of(
                List.of(
                    "As of",
                    PdfTemporalValueFormatter.optionalDate(
                        report.effectiveDateAsOf().orElse(null))))));
  }
}
