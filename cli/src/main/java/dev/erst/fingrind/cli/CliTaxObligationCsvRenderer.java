package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import java.util.ArrayList;
import java.util.List;

/** Renders one tabular tax-obligation CSV export with one row per published record. */
final class CliTaxObligationCsvRenderer {
  private static final List<String> CSV_HEADERS =
      List.of(
          "exportFamily",
          "rowId",
          "parentRowId",
          "relationKind",
          "recordKind",
          "taxRegistrationId",
          "taxRegistrationName",
          "taxJurisdiction",
          "taxRegistrationNumber",
          "effectiveDateFrom",
          "effectiveDateTo",
          "dueDate",
          "taxCode",
          "taxCodeName",
          "applicationKind",
          "postingCount",
          "currencyCode",
          "taxableAmount",
          "taxAmount",
          "grossAmount",
          "outputTax",
          "recoverableInputTax",
          "nonrecoverableInputTax",
          "netPayable",
          "netReceivable",
          "message");

  private CliTaxObligationCsvRenderer() {}

  static String render(TaxObligationReport report) {
    List<List<String>> rows = new ArrayList<>();
    report.codeSummaries().stream().map(summary -> summaryRow(report, summary)).forEach(rows::add);
    rows.add(summaryTotalsRow(report));
    return CliTextFormat.renderCsv(CSV_HEADERS, rows);
  }

  private static List<String> summaryRow(
      TaxObligationReport report, TaxObligationCodeSummary summary) {
    return List.of(
        CliCsvExportFamilies.TAX_OBLIGATION,
        "tax-obligation-row:"
            + summary.taxCode().value()
            + ":"
            + summary.applicationKind().wireValue(),
        "",
        "line",
        CliCsvExportFamilies.TAX_OBLIGATION,
        report.registration().taxRegistrationId().value(),
        report.registration().taxRegistrationName().value(),
        report.registration().jurisdiction().value(),
        report.registration().registrationNumber() == null
            ? ""
            : report.registration().registrationNumber().value(),
        report.reportingPeriod().effectiveDateFrom().toString(),
        report.reportingPeriod().effectiveDateTo().toString(),
        report.dueDate().toString(),
        summary.taxCode().value(),
        summary.taxCodeName().value(),
        summary.applicationKind().wireValue(),
        Integer.toString(summary.postingCount()),
        summary.taxableAmount().currencyCode(),
        CliTextFormat.displayMoney(summary.taxableAmount().toMoney()),
        CliTextFormat.displayMoney(summary.taxAmount().toMoney()),
        CliTextFormat.displayMoney(summary.grossAmount().toMoney()),
        "",
        "",
        "",
        "",
        "",
        "");
  }

  private static List<String> summaryTotalsRow(TaxObligationReport report) {
    return List.of(
        CliCsvExportFamilies.TAX_OBLIGATION,
        "tax-obligation-total:" + report.outputTax().currencyCode(),
        "",
        report.codeSummaries().isEmpty() ? "report-empty" : "report-total",
        CliCsvExportFamilies.TAX_OBLIGATION,
        report.registration().taxRegistrationId().value(),
        report.registration().taxRegistrationName().value(),
        report.registration().jurisdiction().value(),
        report.registration().registrationNumber() == null
            ? ""
            : report.registration().registrationNumber().value(),
        report.reportingPeriod().effectiveDateFrom().toString(),
        report.reportingPeriod().effectiveDateTo().toString(),
        report.dueDate().toString(),
        "",
        "",
        "",
        "",
        report.outputTax().currencyCode(),
        "",
        "",
        "",
        CliTextFormat.displayMoney(report.outputTax().toMoney()),
        CliTextFormat.displayMoney(report.recoverableInputTax().toMoney()),
        CliTextFormat.displayMoney(report.nonrecoverableInputTax().toMoney()),
        CliTextFormat.displayMoney(report.netPayable().toMoney()),
        CliTextFormat.displayMoney(report.netReceivable().toMoney()),
        report.codeSummaries().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("tax obligation code summaries")
            : "");
  }
}
