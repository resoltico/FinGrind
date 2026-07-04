package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.util.ArrayList;
import java.util.List;

/** Text projector for the shared report content model. */
final class TextReportProjector {
  private TextReportProjector() {}

  static String render(ReportModel reportModel) {
    ReportModel model = java.util.Objects.requireNonNull(reportModel, "reportModel");
    List<String> sections = new ArrayList<>();
    if (!model.verdicts().isEmpty()) {
      sections.add(CliTextFormat.renderKeyValueBlock(verdictRows(model.verdicts())));
    }
    for (ReportSection section : model.sections()) {
      sections.add(renderSection(section));
    }
    sections.add(
        CliReportRenderSupport.section(
            "Context", CliTextFormat.renderKeyValueBlock(verdictRows(model.context().rows()))));
    return CliTextFormat.renderTitledBlock(
        model.title(), CliReportRenderSupport.joinSections(sections.toArray(String[]::new)));
  }

  private static String renderSection(ReportSection section) {
    List<String> bodyParts = new ArrayList<>();
    if (!section.verdicts().isEmpty()) {
      bodyParts.add(CliTextFormat.renderKeyValueBlock(verdictRows(section.verdicts())));
    }
    if (!section.rows().isEmpty()) {
      bodyParts.add(renderTable(section.columns(), section.rows()));
    }
    for (ReportTotals totals : section.totals()) {
      bodyParts.add(
          CliReportRenderSupport.section(
              totals.title(), renderTable(totals.columns(), totals.rows())));
    }
    return CliReportRenderSupport.section(
        section.title(),
        bodyParts.isEmpty()
            ? CliTextFormat.renderKeyValueBlock(List.of(List.of("Outcome", "No projected facts.")))
            : CliReportRenderSupport.joinSections(bodyParts.toArray(String[]::new)));
  }

  private static String renderTable(List<ReportColumn> columns, List<ReportRow> rows) {
    int[] rightAlignedColumns =
        java.util.stream.IntStream.range(0, columns.size())
            .filter(index -> columns.get(index).alignment() == ReportColumn.Alignment.RIGHT)
            .toArray();
    return CliTextFormat.renderAdaptiveTable(
        CliReportRenderSupport.TEXT_TABLE_WIDTH,
        columns.stream().map(ReportColumn::title).toList(),
        rows.stream().map(ReportRow::cells).toList(),
        rightAlignedColumns);
  }

  private static List<List<String>> verdictRows(List<ReportVerdict> verdicts) {
    return verdicts.stream().map(verdict -> List.of(verdict.label(), verdict.value())).toList();
  }
}
