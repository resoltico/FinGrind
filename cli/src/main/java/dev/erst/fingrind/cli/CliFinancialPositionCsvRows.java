package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Builds CSV row families for the financial-position report surface. */
final class CliFinancialPositionCsvRows {
  private static final String RECORD_KIND = CliCsvExportFamilies.FINANCIAL_POSITION;

  private CliFinancialPositionCsvRows() {}

  static Stream<List<String>> rows(
      FinancialPositionReport report, String reportBasis, List<FinancialPositionSection> sections) {
    String effectiveDateAsOf = effectiveDateAsOf(report, reportBasis);
    List<List<String>> rows = new ArrayList<>();
    sections.forEach(
        section -> rows.addAll(sectionRows(report, reportBasis, effectiveDateAsOf, section)));
    if (!rows.isEmpty()) {
      return rows.stream();
    }
    return Stream.of(reportEmptyRow(report, reportBasis, effectiveDateAsOf));
  }

  private static String effectiveDateAsOf(FinancialPositionReport report, String reportBasis) {
    return "comparative".equals(reportBasis)
        ? report
            .comparativeEffectiveDateRange()
            .effectiveDateTo()
            .map(LocalDate::toString)
            .orElse("")
        : report.resolvedEffectiveDateAsOf().map(LocalDate::toString).orElse("");
  }

  private static List<List<String>> sectionRows(
      FinancialPositionReport report,
      String reportBasis,
      String effectiveDateAsOf,
      FinancialPositionSection section) {
    List<List<String>> rows = new ArrayList<>();
    section.rows().forEach(row -> rows.add(lineRow(reportBasis, effectiveDateAsOf, section, row)));
    section
        .totals()
        .forEach(
            total -> rows.add(sectionTotalRow(reportBasis, effectiveDateAsOf, section, total)));
    if (rows.isEmpty()) {
      rows.add(sectionEmptyRow(report, reportBasis, effectiveDateAsOf, section));
    }
    return List.copyOf(rows);
  }

  private static List<String> lineRow(
      String reportBasis,
      String effectiveDateAsOf,
      FinancialPositionSection section,
      FinancialPositionRow row) {
    return List.of(
        CliCsvExportFamilies.FINANCIAL_POSITION,
        "financial-position-row:" + reportBasis + ":" + row.lineCode(),
        sectionRowId(reportBasis, section),
        "line",
        reportBasis,
        RECORD_KIND,
        effectiveDateAsOf,
        section.accountType().wireValue(),
        row.lineCode(),
        row.lineName(),
        row.lineType().wireValue(),
        row.lineClassification().map(FinancialPositionLineClassification::wireValue).orElse(""),
        row.lineKind().wireValue(),
        row.balance().netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(row.balance().debitTotal()),
        CliQueryScopeText.displayMoney(row.balance().creditTotal()),
        CliQueryScopeText.displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue(),
        "");
  }

  private static List<String> sectionTotalRow(
      String reportBasis,
      String effectiveDateAsOf,
      FinancialPositionSection section,
      CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.FINANCIAL_POSITION,
        "financial-position-section-total:"
            + reportBasis
            + ":"
            + section.accountType().wireValue()
            + ":"
            + total.netAmount().currencyUnit().code(),
        sectionRowId(reportBasis, section),
        "section-total",
        reportBasis,
        "section-total",
        effectiveDateAsOf,
        section.accountType().wireValue(),
        section.accountType().wireValue().toLowerCase(Locale.ROOT) + "-total",
        CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType()) + " total",
        "",
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
      FinancialPositionReport report,
      String reportBasis,
      String effectiveDateAsOf,
      FinancialPositionSection section) {
    return List.of(
        CliCsvExportFamilies.FINANCIAL_POSITION,
        "financial-position-section-empty:" + reportBasis + ":" + section.accountType().wireValue(),
        sectionRowId(reportBasis, section),
        "section-empty",
        reportBasis,
        RECORD_KIND,
        effectiveDateAsOf,
        section.accountType().wireValue(),
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
        CliReportRenderSupport.emptySectionLinesMessage(
            CliAccountStatementLabels.displayAccountTypeSectionLabel(section.accountType())));
  }

  private static List<String> reportEmptyRow(
      FinancialPositionReport report, String reportBasis, String effectiveDateAsOf) {
    return List.of(
        CliCsvExportFamilies.FINANCIAL_POSITION,
        "financial-position-report-empty:" + reportBasis + ":" + effectiveDateAsOf,
        "",
        "report-empty",
        reportBasis,
        RECORD_KIND,
        effectiveDateAsOf,
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
        CliQueryScopeText.noMatchesLabel("financial position lines"));
  }

  private static String sectionRowId(String reportBasis, FinancialPositionSection section) {
    return "financial-position-section:" + reportBasis + ":" + section.accountType().wireValue();
  }
}
