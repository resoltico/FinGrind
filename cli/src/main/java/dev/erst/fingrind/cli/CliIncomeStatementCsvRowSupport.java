package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.PresentationSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport.SectionCode;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Shared scope and total-row builders for the income-statement CSV surface. */
final class CliIncomeStatementCsvRowSupport {
  private CliIncomeStatementCsvRowSupport() {}

  static String effectiveDateFrom(IncomeStatementReport report, String reportBasis) {
    return "comparative".equals(reportBasis)
        ? report
            .comparativeEffectiveDateRange()
            .effectiveDateFrom()
            .map(LocalDate::toString)
            .orElse("")
        : report.effectiveDateFrom().toString();
  }

  static String effectiveDateTo(IncomeStatementReport report, String reportBasis) {
    return "comparative".equals(reportBasis)
        ? report
            .comparativeEffectiveDateRange()
            .effectiveDateTo()
            .map(LocalDate::toString)
            .orElse("")
        : report.effectiveDateTo().toString();
  }

  static String totalRowId(String reportBasis, PresentationSection section, CurrencyBalance total) {
    return "income-statement-section-total:"
        + reportBasis
        + ":"
        + section.sectionCode().wireValue()
        + ":"
        + total.netAmount().currencyUnit().code();
  }

  static String emptyRowId(String reportBasis, PresentationSection section) {
    return "income-statement-section-empty:"
        + reportBasis
        + ":"
        + section.sectionCode().wireValue();
  }

  static List<String> netIncomeTotalRow(
      String reportBasis, String effectiveDateFrom, String effectiveDateTo, CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.INCOME_STATEMENT,
        "income-statement-total:" + reportBasis + ":" + total.netAmount().currencyUnit().code(),
        "",
        "report-total",
        reportBasis,
        CliCsvExportFamilies.INCOME_STATEMENT,
        effectiveDateFrom,
        effectiveDateTo,
        "NET_INCOME",
        "net-income-total",
        "Net Income Totals",
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

  static List<String> grossProfitTotalRow(
      String reportBasis, String effectiveDateFrom, String effectiveDateTo, CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.INCOME_STATEMENT,
        "income-statement-gross-profit:"
            + reportBasis
            + ":"
            + total.netAmount().currencyUnit().code(),
        "",
        "report-subtotal",
        reportBasis,
        CliCsvExportFamilies.INCOME_STATEMENT,
        effectiveDateFrom,
        effectiveDateTo,
        "GROSS_PROFIT",
        "gross-profit-total",
        "Gross Profit",
        "",
        "",
        "GROSS_PROFIT_TOTAL",
        total.netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(total.debitTotal()),
        CliQueryScopeText.displayMoney(total.creditTotal()),
        CliQueryScopeText.displayMoney(total.netAmount()),
        total.balanceSide().wireValue(),
        "");
  }

  static List<String> reportEmptyRow(
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
        CliCsvExportFamilies.INCOME_STATEMENT,
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

  static String sectionRowId(String reportBasis, PresentationSection section) {
    return "income-statement-section:" + reportBasis + ":" + section.sectionCode().wireValue();
  }

  static String sectionCodeSlug(SectionCode sectionCode) {
    return sectionCode.wireValue().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
