package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Builds CSV row families for the income-statement report surface. */
final class CliIncomeStatementCsvRows {
  private static final String RECORD_KIND = CliCsvExportFamilies.INCOME_STATEMENT;

  private CliIncomeStatementCsvRows() {}

  static Stream<List<String>> rows(
      IncomeStatementReport report,
      String reportBasis,
      List<IncomeStatementSection> sections,
      List<CurrencyBalance> netIncomeTotals) {
    String effectiveDateFrom = effectiveDateFrom(report, reportBasis);
    String effectiveDateTo = effectiveDateTo(report, reportBasis);
    List<List<String>> rows = new ArrayList<>();
    sections.forEach(
        section ->
            rows.addAll(
                sectionRows(report, reportBasis, effectiveDateFrom, effectiveDateTo, section)));
    netIncomeTotals.forEach(
        total ->
            rows.add(netIncomeTotalRow(reportBasis, effectiveDateFrom, effectiveDateTo, total)));
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return Stream.of(reportEmptyRow(report, reportBasis, effectiveDateFrom, effectiveDateTo));
  }

  private static String effectiveDateFrom(IncomeStatementReport report, String reportBasis) {
    return "comparative".equals(reportBasis)
        ? report
            .comparativeEffectiveDateRange()
            .effectiveDateFrom()
            .map(LocalDate::toString)
            .orElse("")
        : report.effectiveDateFrom().toString();
  }

  private static String effectiveDateTo(IncomeStatementReport report, String reportBasis) {
    return "comparative".equals(reportBasis)
        ? report
            .comparativeEffectiveDateRange()
            .effectiveDateTo()
            .map(LocalDate::toString)
            .orElse("")
        : report.effectiveDateTo().toString();
  }

  private static List<List<String>> sectionRows(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section) {
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
      rows.add(sectionEmptyRow(report, reportBasis, effectiveDateFrom, effectiveDateTo, section));
    }
    return List.copyOf(rows);
  }

  private static List<String> lineRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section,
      IncomeStatementRow row) {
    String rowId = "income-statement-row:" + reportBasis + ":" + row.lineCode();
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            row.lineCode(),
            row.lineName(),
            rowId,
            lineDetailColumns(row)),
        "line",
        row.lineKind().wireValue(),
        row.movement());
  }

  private static List<String> sectionTotalRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section,
      CurrencyBalance total) {
    String lineCode = section.accountType().wireValue().toLowerCase(Locale.ROOT) + "-total";
    String lineName =
        CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType()) + " total";
    return CliStatementCsvSectionRowSupport.valuedRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            lineCode,
            lineName,
            totalRowId(reportBasis, section, total),
            totalDetailColumns()),
        "section-total",
        "SECTION_TOTAL",
        total);
  }

  private static List<String> sectionEmptyRow(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section) {
    return CliStatementCsvSectionRowSupport.emptyRow(
        rowSpec(
            reportBasis,
            effectiveDateFrom,
            effectiveDateTo,
            section,
            "",
            "",
            emptyRowId(reportBasis, section),
            totalDetailColumns()),
        report.bookIdentity().functionalCurrency().code(),
        CliReportRenderSupport.emptySectionLinesMessage(
            CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType())));
  }

  private static CliStatementCsvSectionRowSupport.StatementRowSpec rowSpec(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section,
      String lineCode,
      String lineName,
      String rowId,
      List<String> detailColumns) {
    return new CliStatementCsvSectionRowSupport.StatementRowSpec(
        CliCsvExportFamilies.INCOME_STATEMENT,
        rowId,
        sectionRowId(reportBasis, section),
        reportBasis,
        RECORD_KIND,
        effectiveDateFrom,
        effectiveDateTo,
        section.accountType().wireValue(),
        lineCode,
        lineName,
        detailColumns);
  }

  private static List<String> lineDetailColumns(IncomeStatementRow row) {
    return List.of(row.lineType().wireValue(), row.lineClassification().wireValue());
  }

  private static List<String> totalDetailColumns() {
    return List.of("", "");
  }

  private static String totalRowId(
      String reportBasis, IncomeStatementSection section, CurrencyBalance total) {
    return "income-statement-section-total:"
        + reportBasis
        + ":"
        + section.accountType().wireValue()
        + ":"
        + total.netAmount().currencyUnit().code();
  }

  private static String emptyRowId(String reportBasis, IncomeStatementSection section) {
    return "income-statement-section-empty:"
        + reportBasis
        + ":"
        + section.accountType().wireValue();
  }

  private static List<String> netIncomeTotalRow(
      String reportBasis, String effectiveDateFrom, String effectiveDateTo, CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.INCOME_STATEMENT,
        "income-statement-total:" + reportBasis + ":" + total.netAmount().currencyUnit().code(),
        "",
        "report-total",
        reportBasis,
        RECORD_KIND,
        effectiveDateFrom,
        effectiveDateTo,
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
        "");
  }

  private static List<String> reportEmptyRow(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo) {
    return List.of(
        CliCsvExportFamilies.INCOME_STATEMENT,
        "income-statement-report-empty:"
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
        report.bookIdentity().functionalCurrency().code(),
        "",
        "",
        "",
        "",
        CliQueryScopeText.noMatchesLabel("income statement lines"));
  }

  private static String sectionRowId(String reportBasis, IncomeStatementSection section) {
    return "income-statement-section:" + reportBasis + ":" + section.accountType().wireValue();
  }
}
