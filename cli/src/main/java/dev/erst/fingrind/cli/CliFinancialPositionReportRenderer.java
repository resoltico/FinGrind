package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.ArrayList;
import java.util.List;

/** Renders financial-position text and CSV outputs. */
final class CliFinancialPositionReportRenderer {
  private CliFinancialPositionReportRenderer() {}

  static String renderText(FinancialPositionReport report) {
    List<FinancialPositionSection> currentSections =
        CliReportRenderSupport.renderableSections(
            report.sections(), CliReportSurfacePolicy::hasRenderableFinancialPositionSection);
    List<String> currentEmptySections =
        CliReportRenderSupport.emptyAccountTypeSectionLabels(
            report.sections(),
            CliReportSurfacePolicy::hasRenderableFinancialPositionSection,
            FinancialPositionSection::accountType);
    String summary =
        CliTextFormat.renderKeyValueBlock(
            summaryRows(report, currentSections, currentEmptySections));
    String sections = currentSections.isEmpty() ? "" : renderSections(currentSections);
    String comparative =
        !CliReportSurfacePolicy.hasComparative(report)
            ? ""
            : CliReportRenderSupport.section(
                "Comparative Financial Position", renderComparative(report));
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Financial Position",
        CliReportRenderSupport.joinSections(
            summary, sections, comparative, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(FinancialPositionReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateAsOf",
            "accountType",
            "lineCode",
            "lineName",
            "lineRole",
            "lineType",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        (CliReportSurfacePolicy.hasComparative(report)
                ? java.util.stream.Stream.concat(
                    CliFinancialPositionCsvRows.rows(report, "current", report.sections()),
                    CliFinancialPositionCsvRows.rows(
                        report, "comparative", report.comparativeSections()))
                : CliFinancialPositionCsvRows.rows(report, "current", report.sections()))
            .toList());
  }

  private static List<List<String>> summaryRows(
      FinancialPositionReport report,
      List<FinancialPositionSection> renderableSections,
      List<String> emptySections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            CliTemporalScopeText.summaryLabel(OperationId.FINANCIAL_POSITION),
            CliQueryScopeText.upperDateBoundaryLabel(
                report.effectiveDateAsOf().orElse(null),
                report.resolvedEffectiveDateAsOf().orElse(null))));
    rows.add(
        List.of(
            "Accounting equation",
            report.accountingEquationBalanced() ? "Balanced" : "Imbalanced"));
    if (renderableSections.isEmpty()) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("financial position lines")));
    } else {
      rows.add(
          List.of(
              "Sections with data",
              String.join(
                  ", ",
                  CliReportRenderSupport.accountTypeSectionLabels(
                      renderableSections, FinancialPositionSection::accountType))));
    }
    if (!emptySections.isEmpty()) {
      rows.add(List.of("Empty sections", String.join(", ", emptySections)));
    }
    return List.copyOf(rows);
  }

  private static String renderComparative(FinancialPositionReport report) {
    List<FinancialPositionSection> comparativeSections =
        CliReportRenderSupport.renderableSections(
            report.comparativeSections(),
            CliReportSurfacePolicy::hasRenderableFinancialPositionSection);
    List<String> emptySections =
        CliReportRenderSupport.emptyAccountTypeSectionLabels(
            report.comparativeSections(),
            CliReportSurfacePolicy::hasRenderableFinancialPositionSection,
            FinancialPositionSection::accountType);
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            CliTemporalScopeText.summaryLabel(OperationId.FINANCIAL_POSITION),
            CliQueryScopeText.upperDateBoundaryLabel(
                report.comparativeEffectiveDateRange().effectiveDateTo().orElse(null))));
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    if (CliReportSurfacePolicy.hasComparativeData(report)) {
      rows.add(
          List.of(
              "Sections with data",
              String.join(
                  ", ",
                  CliReportRenderSupport.accountTypeSectionLabels(
                      comparativeSections, FinancialPositionSection::accountType))));
    } else {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("financial position lines")));
    }
    if (!emptySections.isEmpty()) {
      rows.add(List.of("Empty sections", String.join(", ", emptySections)));
    }
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(List.copyOf(rows)), renderSections(comparativeSections));
  }

  private static String renderSections(List<FinancialPositionSection> sections) {
    return CliReportRenderSupport.renderAccountTypeStatementSections(
        sections,
        "financial position lines",
        FinancialPositionSection::accountType,
        FinancialPositionSection::rows,
        FinancialPositionSection::totals,
        row ->
            List.of(
                CliAccountStatementLabels.displayStatementLineCode(row.lineCode(), row.lineKind()),
                row.lineName(),
                CliAccountStatementLabels.displayFinancialPositionLineClassification(
                    row.lineClassification()),
                CliQueryScopeText.displayMoney(row.balance().netAmount()),
                CliBalanceOutputFormatter.displayBalanceSideLabel(row.balance().balanceSide())));
  }
}
