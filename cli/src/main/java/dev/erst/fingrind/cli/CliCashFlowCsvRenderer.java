package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Renders cash receipts/payments CSV exports apart from the human-focused text view. */
final class CliCashFlowCsvRenderer {
  private static final String RECORD_KIND = CliCsvExportFamilies.CASH_FLOW_STATEMENT;

  private CliCashFlowCsvRenderer() {}

  static String renderCsv(CashFlowStatementReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
            "sectionKind",
            "lineCode",
            "lineName",
            "lineType",
            "financialPositionLineClassification",
            "profitAndLossLineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        (CliStatementReportSurfacePolicy.hasComparative(report)
                ? Stream.concat(
                    csvRows(
                        report,
                        "current",
                        report.sections(),
                        report.openingCashTotals(),
                        report.movementTotals(),
                        report.closingCashTotals()),
                    csvRows(
                        report,
                        "comparative",
                        report.comparativeSections(),
                        report.comparativeOpeningCashTotals(),
                        report.comparativeMovementTotals(),
                        report.comparativeClosingCashTotals()))
                : csvRows(
                    report,
                    "current",
                    report.sections(),
                    report.openingCashTotals(),
                    report.movementTotals(),
                    report.closingCashTotals()))
            .toList());
  }

  private static Stream<List<String>> csvRows(
      CashFlowStatementReport report,
      String reportBasis,
      List<CashFlowSection> sections,
      List<CurrencyBalance> openingCashTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingCashTotals) {
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
    List<List<String>> rows = new ArrayList<>();
    sections.forEach(
        section ->
            rows.addAll(sectionRows(reportBasis, effectiveDateFrom, effectiveDateTo, section)));
    rows.addAll(
        reportTotals(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            openingCashTotals,
            movementTotals,
            closingCashTotals));
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return Stream.of(
        List.of(
            CliCsvExportFamilies.CASH_FLOW_STATEMENT,
            "cash-flow-statement-report-empty:"
                + reportBasis
                + ":"
                + effectiveDateFrom
                + ":"
                + effectiveDateTo,
            "",
            "report-empty",
            reportBasis,
            RECORD_KIND,
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
            CliQueryScopeText.noMatchesLabel("cash-flow lines")));
  }

  private static List<List<String>> sectionRows(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      CashFlowSection section) {
    List<List<String>> rows = new ArrayList<>();
    section
        .rows()
        .forEach(
            row ->
                rows.add(lineRow(reportBasis, effectiveDateFrom, effectiveDateTo, section, row)));
    section
        .totals()
        .forEach(
            total ->
                rows.add(
                    sectionTotalRow(
                        reportBasis, effectiveDateFrom, effectiveDateTo, section, total)));
    if (rows.isEmpty()) {
      rows.add(sectionEmptyRow(reportBasis, effectiveDateFrom, effectiveDateTo, section));
    }
    return List.copyOf(rows);
  }

  private static List<String> lineRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      CashFlowSection section,
      CashFlowRow row) {
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            row.lineCode(),
            row.lineName(),
            "cash-flow-statement-row:"
                + reportBasis
                + ":"
                + section.sectionKind().wireValue()
                + ":"
                + row.lineCode(),
            List.of(
                row.lineType().wireValue(),
                row.financialPositionLineClassification()
                    .map(dev.erst.fingrind.core.FinancialPositionLineClassification::wireValue)
                    .orElse(""),
                row.profitAndLossLineClassification()
                    .map(dev.erst.fingrind.core.ProfitAndLossLineClassification::wireValue)
                    .orElse(""))),
        "line",
        row.lineKind().wireValue(),
        row.movement());
  }

  private static List<String> sectionTotalRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      CashFlowSection section,
      CurrencyBalance total) {
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            section.sectionKind().wireValue().toLowerCase(Locale.ROOT) + "-total",
            CliAccountStatementLabels.displayCashFlowSectionLabel(section.sectionKind()) + " total",
            "cash-flow-statement-section-total:"
                + reportBasis
                + ":"
                + section.sectionKind().wireValue()
                + ":"
                + total.netAmount().currencyUnit().code(),
            List.of("", "", "")),
        "section-total",
        "SECTION_TOTAL",
        total);
  }

  private static List<String> sectionEmptyRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      CashFlowSection section) {
    return CliStatementCsvSectionRowSupport.emptyRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            "",
            "",
            "cash-flow-statement-section-empty:"
                + reportBasis
                + ":"
                + section.sectionKind().wireValue(),
            List.of("", "", "")),
        "",
        CliReportRenderSupport.emptySectionLinesMessage(
            CliAccountStatementLabels.displayCashFlowSectionLabel(section.sectionKind())));
  }

  private static CliStatementCsvSectionRowSupport.StatementRowSpec rowSpec(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      CashFlowSection section,
      String lineCode,
      String lineName,
      String rowId,
      List<String> detailColumns) {
    return new CliStatementCsvSectionRowSupport.StatementRowSpec(
        CliCsvExportFamilies.CASH_FLOW_STATEMENT,
        rowId,
        sectionRowId(reportBasis, section),
        reportBasis,
        RECORD_KIND,
        effectiveDateFrom,
        effectiveDateTo,
        section.sectionKind().wireValue(),
        lineCode,
        lineName,
        detailColumns);
  }

  private static List<List<String>> reportTotals(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      List<CurrencyBalance> openingCashTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingCashTotals) {
    List<String> currencyCodes =
        Stream.of(openingCashTotals, movementTotals, closingCashTotals)
            .flatMap(List::stream)
            .map(total -> total.netAmount().currencyUnit().code())
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    return currencyCodes.stream()
        .map(
            currencyCode ->
                List.of(
                    CliCsvExportFamilies.CASH_FLOW_STATEMENT,
                    "cash-flow-statement-report-total:" + reportBasis + ":" + currencyCode,
                    "",
                    "report-total",
                    reportBasis,
                    RECORD_KIND,
                    effectiveDateFrom,
                    effectiveDateTo,
                    "",
                    "report-total",
                    "Report total",
                    "",
                    "",
                    "",
                    "REPORT_TOTAL",
                    currencyCode,
                    CliQueryScopeText.displayMoney(
                        CliReportRenderSupport.balanceForCurrency(movementTotals, currencyCode)
                            .debitTotal()),
                    CliQueryScopeText.displayMoney(
                        CliReportRenderSupport.balanceForCurrency(movementTotals, currencyCode)
                            .creditTotal()),
                    CliQueryScopeText.displayMoney(
                        CliReportRenderSupport.balanceForCurrency(movementTotals, currencyCode)
                            .netAmount()),
                    CliReportRenderSupport.balanceForCurrency(movementTotals, currencyCode)
                        .balanceSide()
                        .wireValue(),
                    "Opening="
                        + CliBalanceOutputFormatter.displayBalanceText(
                            CliReportRenderSupport.balanceForCurrency(
                                openingCashTotals, currencyCode))
                        + "; closing="
                        + CliBalanceOutputFormatter.displayBalanceText(
                            CliReportRenderSupport.balanceForCurrency(
                                closingCashTotals, currencyCode))))
        .toList();
  }

  private static String sectionRowId(String reportBasis, CashFlowSection section) {
    return "cash-flow-statement-section:" + reportBasis + ":" + section.sectionKind().wireValue();
  }
}
