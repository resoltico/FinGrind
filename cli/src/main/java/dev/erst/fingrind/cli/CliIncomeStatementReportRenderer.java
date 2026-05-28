package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders income-statement text and CSV outputs. */
final class CliIncomeStatementReportRenderer {
  private CliIncomeStatementReportRenderer() {}

  static String renderText(IncomeStatementReport report) {
    List<IncomeStatementSection> currentSections =
        CliReportRenderSupport.renderableSections(
            report.sections(), CliReportSurfacePolicy::hasRenderableIncomeStatementSection);
    List<String> currentEmptySections =
        CliReportRenderSupport.emptyAccountTypeSectionLabels(
            report.sections(),
            CliReportSurfacePolicy::hasRenderableIncomeStatementSection,
            IncomeStatementSection::accountType);
    String summary =
        CliTextFormat.renderKeyValueBlock(
            summaryRows(
                report.effectiveDateFrom(),
                report.effectiveDateTo(),
                currentSections,
                currentEmptySections,
                report.netIncomeTotals(),
                "Net income totals"));
    String sections = currentSections.isEmpty() ? "" : renderSections(currentSections);
    String comparative =
        !CliReportSurfacePolicy.hasComparative(report)
            ? ""
            : CliReportRenderSupport.section(
                "Comparative Income Statement", renderComparative(report));
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Income Statement",
        CliReportRenderSupport.joinSections(
            summary, sections, comparative, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(IncomeStatementReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
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
                csvRows(report, "current", report.sections(), report.netIncomeTotals()),
                csvRows(
                    report,
                    "comparative",
                    report.comparativeSections(),
                    report.comparativeNetIncomeTotals()))
            .toList());
  }

  private static List<List<String>> summaryRows(
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      List<IncomeStatementSection> renderableSections,
      List<String> emptySections,
      List<CurrencyBalance> netIncomeTotals,
      String netIncomeLabel) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Effective date from", effectiveDateFrom.toString()));
    rows.add(List.of("Effective date to", effectiveDateTo.toString()));
    if (renderableSections.isEmpty() && netIncomeTotals.isEmpty()) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("income statement lines")));
    } else {
      rows.add(List.of(netIncomeLabel, CliReportRenderSupport.joinedBalancesText(netIncomeTotals)));
      if (!renderableSections.isEmpty()) {
        rows.add(
            List.of(
                "Sections with data",
                String.join(
                    ", ",
                    CliReportRenderSupport.accountTypeSectionLabels(
                        renderableSections, IncomeStatementSection::accountType))));
      }
    }
    if (!emptySections.isEmpty()) {
      rows.add(List.of("Empty sections", String.join(", ", emptySections)));
    }
    return List.copyOf(rows);
  }

  private static String renderComparative(IncomeStatementReport report) {
    List<IncomeStatementSection> comparativeSections =
        CliReportRenderSupport.renderableSections(
            report.comparativeSections(),
            CliReportSurfacePolicy::hasRenderableIncomeStatementSection);
    List<String> emptySections =
        CliReportRenderSupport.emptyAccountTypeSectionLabels(
            report.comparativeSections(),
            CliReportSurfacePolicy::hasRenderableIncomeStatementSection,
            IncomeStatementSection::accountType);
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(
            comparativeSummaryRows(report, comparativeSections, emptySections)),
        comparativeSections.isEmpty() ? "" : renderSections(comparativeSections));
  }

  private static List<List<String>> comparativeSummaryRows(
      IncomeStatementReport report,
      List<IncomeStatementSection> comparativeSections,
      List<String> emptySections) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    rows.addAll(
        summaryRows(
            report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .orElse(report.effectiveDateFrom()),
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .orElse(report.effectiveDateTo()),
            comparativeSections,
            emptySections,
            report.comparativeNetIncomeTotals(),
            "Comparative net income totals"));
    return List.copyOf(rows);
  }

  private static String renderSections(List<IncomeStatementSection> sections) {
    return CliReportRenderSupport.renderAccountTypeStatementSections(
        sections,
        "income statement lines",
        IncomeStatementSection::accountType,
        IncomeStatementSection::rows,
        IncomeStatementSection::totals,
        row ->
            List.of(
                CliAccountStatementLabels.displayStatementLineCode(row.lineCode(), row.lineKind()),
                row.lineName(),
                CliAccountStatementLabels.displayProfitAndLossLineClassification(
                    row.lineClassification()),
                CliQueryScopeText.displayMoney(row.movement().netAmount()),
                CliBalanceOutputFormatter.displayBalanceSideLabel(row.movement().balanceSide())));
  }

  private static java.util.stream.Stream<List<String>> csvRows(
      IncomeStatementReport report,
      String reportBasis,
      List<IncomeStatementSection> sections,
      List<CurrencyBalance> netIncomeTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    List<List<String>> sectionRows =
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
                                  effectiveDateFrom,
                                  effectiveDateTo,
                                  section.accountType().wireValue(),
                                  row.lineCode(),
                                  row.lineName(),
                                  row.lineRole().map(AccountRole::wireValue).orElse(""),
                                  row.lineType().wireValue(),
                                  row.lineClassification().wireValue(),
                                  row.lineKind().wireValue(),
                                  row.movement().netAmount().currencyUnit().code(),
                                  CliQueryScopeText.displayMoney(row.movement().debitTotal()),
                                  CliQueryScopeText.displayMoney(row.movement().creditTotal()),
                                  CliQueryScopeText.displayMoney(row.movement().netAmount()),
                                  row.movement().balanceSide().wireValue(),
                                  ""))
                      .forEach(renderedRows::add);
                  section.totals().stream()
                      .map(
                          total ->
                              List.of(
                                  reportBasis,
                                  "section-total",
                                  effectiveDateFrom,
                                  effectiveDateTo,
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
                            effectiveDateFrom,
                            effectiveDateTo,
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
    List<List<String>> totalRows =
        netIncomeTotals.stream()
            .map(
                total ->
                    List.of(
                        reportBasis,
                        "net-income-total",
                        effectiveDateFrom,
                        effectiveDateTo,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "NET_INCOME_TOTAL",
                        total.netAmount().currencyUnit().code(),
                        CliQueryScopeText.displayMoney(total.debitTotal()),
                        CliQueryScopeText.displayMoney(total.creditTotal()),
                        CliQueryScopeText.displayMoney(total.netAmount()),
                        total.balanceSide().wireValue(),
                        ""))
            .toList();
    List<List<String>> rows = new ArrayList<>(sectionRows);
    rows.addAll(totalRows);
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return java.util.stream.Stream.of(
        List.of(
            reportBasis,
            CliCsvEmptyKinds.REPORT_EMPTY,
            effectiveDateFrom,
            effectiveDateTo,
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
            CliQueryScopeText.noMatchesLabel("income statement lines")));
  }
}
