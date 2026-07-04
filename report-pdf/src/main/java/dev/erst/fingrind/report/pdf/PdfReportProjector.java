package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Projects the shared report content model into one PDF document body. */
final class PdfReportProjector {
  void render(PdfPageWriter pageWriter, ReportModel reportModel) throws IOException {
    Objects.requireNonNull(pageWriter, "pageWriter");
    Objects.requireNonNull(reportModel, "reportModel");
    if (!reportModel.verdicts().isEmpty()) {
      pageWriter.writeKeyValueTable("Summary", verdictRows(reportModel.verdicts()));
    }
    for (ReportSection section : reportModel.sections()) {
      renderSection(pageWriter, section);
    }
    pageWriter.writeKeyValueTable("Context", verdictRows(reportModel.context().rows()));
  }

  private void renderSection(PdfPageWriter pageWriter, ReportSection section) throws IOException {
    if (!section.verdicts().isEmpty()) {
      pageWriter.writeKeyValueTable(section.title(), verdictRows(section.verdicts()));
    }
    if (!section.rows().isEmpty()) {
      PdfStatementSectionTableRenderer.renderSection(pageWriter, section);
    }
    for (ReportTotals totals : section.totals()) {
      pageWriter.writeTable(
          totals.title(),
          PdfReportTableLayouts.reportColumns(totals.columns()),
          totals.rows().stream()
              .map(dev.erst.fingrind.contract.reportmodel.ReportRow::cells)
              .toList());
    }
  }

  private static List<List<String>> verdictRows(List<ReportVerdict> verdicts) {
    return verdicts.stream().map(verdict -> List.of(verdict.label(), verdict.value())).toList();
  }
}
