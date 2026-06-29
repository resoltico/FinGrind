package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders statements of cash receipts and payments as text and CSV. */
final class CliCashFlowReportRenderer {
  private CliCashFlowReportRenderer() {}

  static String renderText(CashFlowStatementReport report) {
    List<CashFlowSection> currentSections =
        CliReportRenderSupport.renderableSections(
            report.sections(), CliStatementSectionSurfacePolicy::hasRenderableCashFlowSection);
    List<String> currentEmptySections = emptySectionLabels(report.sections());
    String summary =
        CliTextFormat.renderKeyValueBlock(
            summaryRows(report, currentSections, currentEmptySections));
    String sections = currentSections.isEmpty() ? "" : renderSections(currentSections);
    String comparative =
        !CliStatementReportSurfacePolicy.hasComparative(report)
            ? ""
            : CliReportRenderSupport.section(
                "Comparative Cash Receipts And Payments",
                CliReportRenderSupport.joinSections(
                    CliTextFormat.renderKeyValueBlock(comparativeSummaryRows(report)),
                    renderComparativeSections(report)));
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Cash Receipts And Payments",
        CliReportRenderSupport.joinSections(
            summary, sections, comparative, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(CashFlowStatementReport report) {
    return CliCashFlowCsvRenderer.renderCsv(report);
  }

  private static List<List<String>> summaryRows(
      CashFlowStatementReport report,
      List<CashFlowSection> renderableSections,
      List<String> emptySections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            CliTemporalScopeText.lowerLabel(OperationId.CASH_FLOW_STATEMENT),
            report.effectiveDateFrom().toString()));
    rows.add(
        List.of(
            CliTemporalScopeText.upperLabel(OperationId.CASH_FLOW_STATEMENT),
            report.effectiveDateTo().toString()));
    rows.add(
        List.of(
            "Opening cash totals",
            CliReportRenderSupport.joinedBalancesText(report.openingCashTotals())));
    rows.add(
        List.of(
            "Movement totals", CliReportRenderSupport.joinedBalancesText(report.movementTotals())));
    rows.add(
        List.of(
            "Closing cash totals",
            CliReportRenderSupport.joinedBalancesText(report.closingCashTotals())));
    if (renderableSections.isEmpty() && report.movementTotals().isEmpty()) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("cash-flow lines")));
    } else if (!renderableSections.isEmpty()) {
      rows.add(List.of("Sections with data", String.join(", ", sectionLabels(renderableSections))));
    }
    if (!emptySections.isEmpty()) {
      rows.add(List.of("Empty sections", String.join(", ", emptySections)));
    }
    return List.copyOf(rows);
  }

  private static List<List<String>> comparativeSummaryRows(CashFlowStatementReport report) {
    List<CashFlowSection> comparativeSections =
        CliReportRenderSupport.renderableSections(
            report.comparativeSections(),
            CliStatementSectionSurfacePolicy::hasRenderableCashFlowSection);
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    rows.addAll(
        summaryRowsForComparative(
            report, comparativeSections, emptySectionLabels(report.comparativeSections())));
    return List.copyOf(rows);
  }

  private static List<List<String>> summaryRowsForComparative(
      CashFlowStatementReport report,
      List<CashFlowSection> comparativeSections,
      List<String> emptySections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            CliTemporalScopeText.lowerLabel(OperationId.CASH_FLOW_STATEMENT),
            report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateFrom().toString())));
    rows.add(
        List.of(
            CliTemporalScopeText.upperLabel(OperationId.CASH_FLOW_STATEMENT),
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateTo().toString())));
    rows.add(
        List.of(
            "Comparative opening cash totals",
            CliReportRenderSupport.joinedBalancesText(report.comparativeOpeningCashTotals())));
    rows.add(
        List.of(
            "Comparative movement totals",
            CliReportRenderSupport.joinedBalancesText(report.comparativeMovementTotals())));
    rows.add(
        List.of(
            "Comparative closing cash totals",
            CliReportRenderSupport.joinedBalancesText(report.comparativeClosingCashTotals())));
    if (comparativeSections.isEmpty() && report.comparativeMovementTotals().isEmpty()) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("cash-flow lines")));
    } else if (!comparativeSections.isEmpty()) {
      rows.add(
          List.of("Sections with data", String.join(", ", sectionLabels(comparativeSections))));
    }
    if (!emptySections.isEmpty()) {
      rows.add(List.of("Empty sections", String.join(", ", emptySections)));
    }
    return rows;
  }

  private static String renderComparativeSections(CashFlowStatementReport report) {
    List<CashFlowSection> comparativeSections =
        CliReportRenderSupport.renderableSections(
            report.comparativeSections(),
            CliStatementSectionSurfacePolicy::hasRenderableCashFlowSection);
    return comparativeSections.isEmpty() ? "" : renderSections(comparativeSections);
  }

  private static String renderSections(List<CashFlowSection> sections) {
    return CliReportRenderSupport.renderStatementSections(
        sections,
        "cash-flow lines",
        section -> CliAccountStatementLabels.displayCashFlowSectionLabel(section.sectionKind()),
        CashFlowSection::rows,
        CashFlowSection::totals,
        CliCashFlowReportRenderer::textRow);
  }

  private static List<String> textRow(CashFlowRow row) {
    return List.of(
        CliAccountStatementLabels.displayStatementLineCode(row.lineCode(), row.lineKind()),
        row.lineName(),
        displayClassification(row),
        CliQueryScopeText.displayMoney(row.movement().netAmount()),
        CliBalanceOutputFormatter.displayBalanceSideLabel(row.movement().balanceSide()));
  }

  private static String displayClassification(CashFlowRow row) {
    String typeLabel = CliAccountStatementLabels.displayLineTypeLabel(row.lineType());
    String detailedLabel =
        row.financialPositionLineClassification()
            .map(CliAccountStatementLabels::displayFinancialPositionLineClassification)
            .or(
                () ->
                    row.profitAndLossLineClassification()
                        .map(CliAccountStatementLabels::displayProfitAndLossLineClassification))
            .orElse(CliHumanDisplay.calculatedLineLabel());
    return "%s (%s)".formatted(typeLabel, detailedLabel);
  }

  private static List<String> emptySectionLabels(List<CashFlowSection> sections) {
    return sections.stream()
        .filter(section -> !CliStatementSectionSurfacePolicy.hasRenderableCashFlowSection(section))
        .map(
            section -> CliAccountStatementLabels.displayCashFlowSectionLabel(section.sectionKind()))
        .toList();
  }

  private static List<String> sectionLabels(List<CashFlowSection> sections) {
    return sections.stream()
        .map(
            section -> CliAccountStatementLabels.displayCashFlowSectionLabel(section.sectionKind()))
        .toList();
  }
}
