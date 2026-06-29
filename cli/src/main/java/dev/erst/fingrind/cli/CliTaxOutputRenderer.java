package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import java.util.ArrayList;
import java.util.List;

/** Renders tax-context success payloads for text and CSV output modes. */
final class CliTaxOutputRenderer {
  private static final String TAX_OBLIGATION_RECORD_KIND = "taxObligationCodeSummary";

  private CliTaxOutputRenderer() {}

  static String renderTaxRegistrationMutationText(
      String outcome, DeclaredTaxRegistration registration) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationMutationText(
        outcome, registration);
  }

  static String renderTaxRegistrationListText(TaxRegistrationPage page) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationListText(page);
  }

  static String renderTaxRegistrationListCsv(TaxRegistrationPage page) {
    return CliTaxRegistrationOutputRenderer.renderTaxRegistrationListCsv(page);
  }

  static String renderTaxObligationText(TaxObligationReport report) {
    List<List<String>> summaryRows =
        new ArrayList<>(CliBookIdentityDisplay.contextRows(report.bookIdentity()));
    summaryRows.add(
        List.of("Tax registration id", report.registration().taxRegistrationId().value()));
    summaryRows.add(
        List.of("Tax registration name", report.registration().taxRegistrationName().value()));
    summaryRows.add(List.of("Jurisdiction", report.registration().jurisdiction().value()));
    summaryRows.add(
        List.of(
            "Reporting period",
            report.reportingPeriod().effectiveDateFrom()
                + " to "
                + report.reportingPeriod().effectiveDateTo()));
    summaryRows.add(List.of("Due date", report.dueDate().toString()));
    summaryRows.add(List.of("Output tax", displayAmount(report.outputTax())));
    summaryRows.add(List.of("Recoverable input tax", displayAmount(report.recoverableInputTax())));
    summaryRows.add(
        List.of("Nonrecoverable input tax", displayAmount(report.nonrecoverableInputTax())));
    summaryRows.add(List.of("Net payable", displayAmount(report.netPayable())));
    summaryRows.add(List.of("Net receivable", displayAmount(report.netReceivable())));
    String codeSummaryTable =
        report.codeSummaries().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("tax obligation code summaries")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Tax code", "Name", "Application", "Postings", "Taxable", "Tax", "Gross"),
                report.codeSummaries().stream()
                    .map(CliTaxOutputRenderer::taxObligationCodeSummaryRow)
                    .toList(),
                3,
                4,
                5,
                6);
    return CliTextFormat.renderTitledBlock(
        "Tax Obligation",
        CliReportRenderSupport.joinSections(
            CliTextFormat.renderKeyValueBlock(summaryRows),
            CliReportRenderSupport.section("Code summaries", codeSummaryTable)));
  }

  static String renderTaxObligationCsv(TaxObligationReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "recordKind",
            "taxRegistrationId",
            "taxRegistrationName",
            "jurisdiction",
            "periodStart",
            "periodEnd",
            "dueDate",
            "taxCode",
            "taxCodeName",
            "applicationKind",
            "postingCount",
            "taxableCurrency",
            "taxableAmount",
            "taxCurrency",
            "taxAmount",
            "grossCurrency",
            "grossAmount",
            "outputTax",
            "recoverableInputTax",
            "nonrecoverableInputTax",
            "netPayable",
            "netReceivable",
            "message"),
        report.codeSummaries().isEmpty()
            ? List.of(taxObligationEmptyCsvRow(report))
            : report.codeSummaries().stream()
                .map(summary -> taxObligationCsvRow(report, summary))
                .toList());
  }

  private static List<String> taxObligationCodeSummaryRow(TaxObligationCodeSummary summary) {
    return List.of(
        summary.taxCode().value(),
        summary.taxCodeName().value(),
        CliTextDisplay.wireLabel(summary.applicationKind().wireValue()),
        Integer.toString(summary.postingCount()),
        displayAmount(summary.taxableAmount()),
        displayAmount(summary.taxAmount()),
        displayAmount(summary.grossAmount()));
  }

  private static List<String> taxObligationCsvRow(
      TaxObligationReport report, TaxObligationCodeSummary summary) {
    return List.of(
        CliCsvExportFamilies.TAX_OBLIGATION,
        "taxObligationCode:"
            + report.registration().taxRegistrationId().value()
            + ":"
            + summary.taxCode().value()
            + ":"
            + summary.applicationKind().wireValue(),
        TAX_OBLIGATION_RECORD_KIND,
        report.registration().taxRegistrationId().value(),
        report.registration().taxRegistrationName().value(),
        report.registration().jurisdiction().value(),
        report.reportingPeriod().effectiveDateFrom().toString(),
        report.reportingPeriod().effectiveDateTo().toString(),
        report.dueDate().toString(),
        summary.taxCode().value(),
        summary.taxCodeName().value(),
        summary.applicationKind().wireValue(),
        Integer.toString(summary.postingCount()),
        summary.taxableAmount().currencyCode(),
        summary.taxableAmount().canonicalDecimal(),
        summary.taxAmount().currencyCode(),
        summary.taxAmount().canonicalDecimal(),
        summary.grossAmount().currencyCode(),
        summary.grossAmount().canonicalDecimal(),
        report.outputTax().canonicalDecimal(),
        report.recoverableInputTax().canonicalDecimal(),
        report.nonrecoverableInputTax().canonicalDecimal(),
        report.netPayable().canonicalDecimal(),
        report.netReceivable().canonicalDecimal(),
        "");
  }

  private static List<String> taxObligationEmptyCsvRow(TaxObligationReport report) {
    return List.of(
        CliCsvExportFamilies.TAX_OBLIGATION,
        "taxObligationScopeEmpty",
        TAX_OBLIGATION_RECORD_KIND,
        report.registration().taxRegistrationId().value(),
        report.registration().taxRegistrationName().value(),
        report.registration().jurisdiction().value(),
        report.reportingPeriod().effectiveDateFrom().toString(),
        report.reportingPeriod().effectiveDateTo().toString(),
        report.dueDate().toString(),
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        report.outputTax().canonicalDecimal(),
        report.recoverableInputTax().canonicalDecimal(),
        report.nonrecoverableInputTax().canonicalDecimal(),
        report.netPayable().canonicalDecimal(),
        report.netReceivable().canonicalDecimal(),
        CliQueryScopeText.noMatchesLabel("tax obligation code summaries"));
  }

  private static String displayAmount(MonetaryAmount amount) {
    return amount.currencyCode() + " " + amount.canonicalDecimal();
  }
}
