package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** CSV projector for the shared report content model. */
final class CsvReportProjector {
  private static final List<String> HEADERS =
      List.of(
          "exportFamily",
          "reportTitle",
          "sectionKey",
          "sectionTitle",
          "blockKind",
          "blockTitle",
          "rowId",
          "columnKey",
          "columnTitle",
          "value");

  private CsvReportProjector() {}

  static String render(ReportModel reportModel) {
    ReportModel model = java.util.Objects.requireNonNull(reportModel, "reportModel");
    List<List<String>> rows = new ArrayList<>();
    CsvScope scope = new CsvScope(model.family(), model.title());
    scope.selectSection("summary", "Summary");
    appendVerdicts(rows, scope, "verdict", "summary", model.verdicts());
    scope.selectSection("context", "Context");
    appendVerdicts(rows, scope, "context", "context", model.context().rows());
    for (ReportSection section : model.sections()) {
      scope.selectSection(section.key(), section.title());
      appendVerdicts(rows, scope, "verdict", section.key() + ":verdict", section.verdicts());
      appendTableRows(rows, scope, null, section.columns(), section.rows());
      for (ReportTotals totals : section.totals()) {
        appendTableRows(rows, scope, totals.title(), totals.columns(), totals.rows());
      }
    }
    return CliTextFormat.renderCsv(HEADERS, rows);
  }

  private static void appendVerdicts(
      List<List<String>> rows,
      CsvScope scope,
      String blockKind,
      String rowIdPrefix,
      List<ReportVerdict> verdicts) {
    for (int index = 0; index < verdicts.size(); index++) {
      ReportVerdict verdict = verdicts.get(index);
      String rowId = rowIdPrefix + ":" + index;
      rows.add(
          csvRow(scope, blockKind, scope.sectionTitle(), rowId, "label", "Label", verdict.label()));
      rows.add(
          csvRow(scope, blockKind, scope.sectionTitle(), rowId, "value", "Value", verdict.value()));
    }
  }

  private static void appendTableRows(
      List<List<String>> rows,
      CsvScope scope,
      @Nullable String totalsTitle,
      List<ReportColumn> columns,
      List<ReportRow> reportRows) {
    String blockKind = totalsTitle == null ? "table" : "totals";
    String blockTitle = totalsTitle == null ? scope.sectionTitle() : totalsTitle;
    for (ReportRow reportRow : reportRows) {
      for (int index = 0; index < columns.size(); index++) {
        ReportColumn column = columns.get(index);
        rows.add(
            csvRow(
                scope,
                blockKind,
                blockTitle,
                reportRow.rowId(),
                column.key(),
                column.title(),
                reportRow.cells().get(index)));
      }
    }
  }

  private static List<String> csvRow(
      CsvScope scope,
      String blockKind,
      String blockTitle,
      String rowId,
      String columnKey,
      String columnTitle,
      String value) {
    return List.of(
        scope.family(),
        scope.reportTitle(),
        scope.sectionKey(),
        scope.sectionTitle(),
        blockKind,
        blockTitle,
        rowId,
        columnKey,
        columnTitle,
        value);
  }

  /** Reusable row-scope holder that avoids per-iteration allocation in the projector loops. */
  private static final class CsvScope {
    private final String family;
    private final String reportTitle;
    private String sectionKey = "";
    private String sectionTitle = "";

    private CsvScope(String family, String reportTitle) {
      this.family = family;
      this.reportTitle = reportTitle;
    }

    private String family() {
      return family;
    }

    private String reportTitle() {
      return reportTitle;
    }

    private String sectionKey() {
      return sectionKey;
    }

    private String sectionTitle() {
      return sectionTitle;
    }

    private void selectSection(String sectionKey, String sectionTitle) {
      this.sectionKey = sectionKey;
      this.sectionTitle = sectionTitle;
    }
  }
}
