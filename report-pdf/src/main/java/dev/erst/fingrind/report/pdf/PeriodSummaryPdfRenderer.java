package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Renders period-summary reports as PDF documents. */
final class PeriodSummaryPdfRenderer {
  void render(PdfPageWriter pageWriter, PeriodSummaryReport report) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(report, "report");
    pageWriter.writeKeyValueTable(
        "Summary",
        PdfStatementMetadataRows.reportParameters(
            report.bookIdentity(),
            report.postingCoverage(),
            List.of(
                List.of("Effective date from", report.effectiveDateFrom().toString()),
                List.of("Effective date to", report.effectiveDateTo().toString()),
                List.of("Posting count", Integer.toString(report.postingCount())),
                List.of("Posting line count", Integer.toString(report.postingLineCount())),
                List.of("Accounts touched", Integer.toString(report.accountsTouched())))));
    pageWriter.writeTable(
        "Currency Totals",
        PdfReportTableLayouts.detailedCurrencyBalanceColumns(),
        report.currencyTotals().stream()
            .map(summary -> PdfBalanceTableSupport.detailedRow(summary.totals()))
            .toList());
    PdfAccountActivityTableSupport.writePeriodAccountActivityTable(
        pageWriter, "Account Activity", report.accountActivity());
  }
}
