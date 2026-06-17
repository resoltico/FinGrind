package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders changes-in-equity text and CSV outputs. */
final class CliChangesInEquityReportRenderer {
  private CliChangesInEquityReportRenderer() {}

  static String renderText(ChangesInEquityReport report) {
    boolean hasCurrent = CliReportSurfacePolicy.hasCurrent(report);
    String summary = CliTextFormat.renderKeyValueBlock(summaryRows(report, hasCurrent));
    String table =
        report.rows().isEmpty()
            ? ""
            : CliReportRenderSupport.section("Equity lines", renderTable(report.rows()));
    String comparative =
        CliReportSurfacePolicy.hasComparative(report)
            ? CliReportRenderSupport.section(
                "Comparative Changes In Equity",
                CliReportRenderSupport.joinSections(
                    CliTextFormat.renderKeyValueBlock(comparativeSummaryRows(report)),
                    report.comparativeRows().isEmpty()
                        ? ""
                        : CliReportRenderSupport.section(
                            "Equity lines", renderTable(report.comparativeRows()))))
            : "";
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Changes In Equity",
        CliReportRenderSupport.joinSections(
            summary, table, comparative, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(ChangesInEquityReport report) {
    return CliChangesInEquityCsvRenderer.renderCsv(report);
  }

  private static List<List<String>> summaryRows(ChangesInEquityReport report, boolean hasCurrent) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            CliTemporalScopeText.lowerLabel(OperationId.CHANGES_IN_EQUITY),
            report.effectiveDateFrom().toString()));
    rows.add(
        List.of(
            CliTemporalScopeText.upperLabel(OperationId.CHANGES_IN_EQUITY),
            report.effectiveDateTo().toString()));
    if (!report.openingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Opening totals", CliReportRenderSupport.joinedBalancesText(report.openingTotals())));
    }
    if (!report.movementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Movement totals",
              CliReportRenderSupport.joinedBalancesText(report.movementTotals())));
    }
    if (!report.closingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Closing totals", CliReportRenderSupport.joinedBalancesText(report.closingTotals())));
    }
    if (!hasCurrent) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("equity lines")));
    }
    return List.copyOf(rows);
  }

  private static String renderTable(List<ChangesInEquityRow> rows) {
    return CliTextFormat.renderAdaptiveTable(
        CliReportRenderSupport.TEXT_TABLE_WIDTH,
        List.of("Line code", "Line name", "Opening", "Movement", "Closing", "Closing side"),
        rows.stream()
            .map(
                row ->
                    List.of(
                        CliAccountStatementLabels.displayStatementLineCode(
                            row.lineCode(), row.lineKind()),
                        row.lineName(),
                        CliQueryScopeText.displayMoney(row.openingBalance().netAmount()),
                        CliQueryScopeText.displayMoney(row.movement().netAmount()),
                        CliQueryScopeText.displayMoney(row.closingBalance().netAmount()),
                        CliBalanceOutputFormatter.displayBalanceSideLabel(
                            row.closingBalance().balanceSide())))
            .toList(),
        2,
        3);
  }

  private static List<List<String>> comparativeSummaryRows(ChangesInEquityReport report) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    rows.add(
        List.of(
            CliTemporalScopeText.lowerLabel(OperationId.CHANGES_IN_EQUITY),
            report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateFrom().toString())));
    rows.add(
        List.of(
            CliTemporalScopeText.upperLabel(OperationId.CHANGES_IN_EQUITY),
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateTo().toString())));
    if (!report.comparativeOpeningTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative opening totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeOpeningTotals())));
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative movement totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeMovementTotals())));
    }
    if (!report.comparativeClosingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative closing totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeClosingTotals())));
    }
    if (!CliReportSurfacePolicy.hasComparativeData(report)) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("equity lines")));
    }
    return List.copyOf(rows);
  }
}
