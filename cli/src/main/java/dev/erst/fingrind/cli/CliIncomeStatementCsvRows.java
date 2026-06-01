package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Builds CSV row families for the income-statement report surface. */
final class CliIncomeStatementCsvRows {
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
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "income-statement-row:" + reportBasis + ":" + row.lineCode(),
        sectionRowId(reportBasis, section),
        "line",
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
        "");
  }

  private static List<String> sectionTotalRow(
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section,
      CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "income-statement-section-total:"
            + reportBasis
            + ":"
            + section.accountType().wireValue()
            + ":"
            + total.netAmount().currencyUnit().code(),
        sectionRowId(reportBasis, section),
        "section-total",
        reportBasis,
        "section-total",
        effectiveDateFrom,
        effectiveDateTo,
        section.accountType().wireValue(),
        section.accountType().wireValue().toLowerCase(Locale.ROOT) + "-total",
        CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType()) + " total",
        "",
        section.accountType().wireValue(),
        "",
        "SECTION_TOTAL",
        total.netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(total.debitTotal()),
        CliQueryScopeText.displayMoney(total.creditTotal()),
        CliQueryScopeText.displayMoney(total.netAmount()),
        total.balanceSide().wireValue(),
        "");
  }

  private static List<String> sectionEmptyRow(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo,
      IncomeStatementSection section) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "income-statement-section-empty:" + reportBasis + ":" + section.accountType().wireValue(),
        sectionRowId(reportBasis, section),
        "section-empty",
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
            CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType())));
  }

  private static List<String> netIncomeTotalRow(
      String reportBasis, String effectiveDateFrom, String effectiveDateTo, CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "income-statement-total:" + reportBasis + ":" + total.netAmount().currencyUnit().code(),
        "",
        "report-total",
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
        "");
  }

  private static List<String> reportEmptyRow(
      IncomeStatementReport report,
      String reportBasis,
      String effectiveDateFrom,
      String effectiveDateTo) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "income-statement-report-empty:"
            + reportBasis
            + ":"
            + effectiveDateFrom
            + ":"
            + effectiveDateTo,
        "",
        "report-empty",
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
        CliQueryScopeText.noMatchesLabel("income statement lines"));
  }

  private static String sectionRowId(String reportBasis, IncomeStatementSection section) {
    return "income-statement-section:" + reportBasis + ":" + section.accountType().wireValue();
  }
}
