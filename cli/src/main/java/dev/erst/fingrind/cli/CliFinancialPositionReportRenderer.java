package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.time.LocalDate;
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
        java.util.stream.Stream.concat(
                csvRows(report, "current", report.sections()),
                csvRows(report, "comparative", report.comparativeSections()))
            .toList());
  }

  private static List<List<String>> summaryRows(
      FinancialPositionReport report,
      List<FinancialPositionSection> renderableSections,
      List<String> emptySections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "As of",
            CliQueryScopeText.upperDateBoundaryLabel(report.effectiveDateAsOf().orElse(null))));
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
            "As of",
            CliQueryScopeText.upperDateBoundaryLabel(
                report.comparativeEffectiveDateRange().effectiveDateTo().orElse(null))));
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    rows.add(
        List.of(
            "Sections with data",
            String.join(
                ", ",
                CliReportRenderSupport.accountTypeSectionLabels(
                    comparativeSections, FinancialPositionSection::accountType))));
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

  private static java.util.stream.Stream<List<String>> csvRows(
      FinancialPositionReport report, String reportBasis, List<FinancialPositionSection> sections) {
    String effectiveDateAsOf =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateAsOf().map(LocalDate::toString).orElse("");
    List<List<String>> rows =
        sections.stream()
            .flatMap(
                section -> {
                  List<List<String>> renderedRows = new ArrayList<>();
                  section.rows().stream()
                      .map(
                          row ->
                              List.of(
                                  reportBasis,
                                  "row",
                                  effectiveDateAsOf,
                                  section.accountType().wireValue(),
                                  row.lineCode(),
                                  row.lineName(),
                                  row.lineRole().map(AccountRole::wireValue).orElse(""),
                                  row.lineType().wireValue(),
                                  row.lineClassification()
                                      .map(FinancialPositionLineClassification::wireValue)
                                      .orElse(""),
                                  row.lineKind().wireValue(),
                                  row.balance().netAmount().currencyUnit().code(),
                                  CliQueryScopeText.displayMoney(row.balance().debitTotal()),
                                  CliQueryScopeText.displayMoney(row.balance().creditTotal()),
                                  CliQueryScopeText.displayMoney(row.balance().netAmount()),
                                  row.balance().balanceSide().wireValue(),
                                  ""))
                      .forEach(renderedRows::add);
                  section.totals().stream()
                      .map(
                          total ->
                              List.of(
                                  reportBasis,
                                  "section-total",
                                  effectiveDateAsOf,
                                  section.accountType().wireValue(),
                                  section
                                          .accountType()
                                          .wireValue()
                                          .toLowerCase(java.util.Locale.ROOT)
                                      + "-total",
                                  CliAccountStatementLabels.displayAccountTypeSectionLabel(
                                          section.accountType())
                                      + " total",
                                  "",
                                  section.accountType().wireValue(),
                                  "",
                                  "SECTION_TOTAL",
                                  total.netAmount().currencyUnit().code(),
                                  CliQueryScopeText.displayMoney(total.debitTotal()),
                                  CliQueryScopeText.displayMoney(total.creditTotal()),
                                  CliQueryScopeText.displayMoney(total.netAmount()),
                                  total.balanceSide().wireValue(),
                                  ""))
                      .forEach(renderedRows::add);
                  if (renderedRows.isEmpty()) {
                    renderedRows.add(
                        List.of(
                            reportBasis,
                            CliCsvEmptyKinds.SECTION_EMPTY,
                            effectiveDateAsOf,
                            section.accountType().wireValue(),
                            "",
                            "",
                            "",
                            section.accountType().wireValue(),
                            "",
                            "",
                            report.bookIdentity().functionalCurrency().code(),
                            "",
                            "",
                            "",
                            "",
                            CliReportRenderSupport.emptySectionLinesMessage(
                                CliAccountStatementLabels.displayAccountTypeSectionLabel(
                                    section.accountType()))));
                  }
                  return renderedRows.stream();
                })
            .toList();
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return java.util.stream.Stream.of(
        List.of(
            reportBasis,
            CliCsvEmptyKinds.REPORT_EMPTY,
            effectiveDateAsOf,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            report.bookIdentity().functionalCurrency().code(),
            "",
            "",
            "",
            "",
            CliQueryScopeText.noMatchesLabel("financial position lines")));
  }
}
