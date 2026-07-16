package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliTaxReportJsonModels;
import java.util.ArrayList;
import java.util.List;

/** Renders the typed CSV row table for tax obligations. */
final class CliTaxObligationCsvRenderer {
  private CliTaxObligationCsvRenderer() {}

  static String render(CliTaxReportJsonModels.TaxObligationPayload report) {
    List<List<String>> rows = new ArrayList<>();
    for (CliTaxReportJsonModels.TaxObligationRowPayload row : report.rows()) {
      rows.add(
          List.of(
              report.family(),
              row.taxCode(),
              row.taxCodeName(),
              row.application(),
              Integer.toString(row.postings()),
              row.taxable().currencyCode(),
              row.taxable().minorUnits(),
              row.tax().currencyCode(),
              row.tax().minorUnits(),
              row.gross().currencyCode(),
              row.gross().minorUnits(),
              report.totals().outputTax().currencyCode(),
              report.totals().outputTax().minorUnits(),
              report.totals().recoverableInputTax().currencyCode(),
              report.totals().recoverableInputTax().minorUnits(),
              report.totals().nonrecoverableInputTax().currencyCode(),
              report.totals().nonrecoverableInputTax().minorUnits(),
              report.totals().netPayable().currencyCode(),
              report.totals().netPayable().minorUnits(),
              report.totals().netReceivable().currencyCode(),
              report.totals().netReceivable().minorUnits()));
    }
    return CliTextFormat.renderCsv(
        List.of(
            "family",
            "taxCode",
            "taxCodeName",
            "application",
            "postings",
            "taxableCurrencyCode",
            "taxableMinorUnits",
            "taxCurrencyCode",
            "taxMinorUnits",
            "grossCurrencyCode",
            "grossMinorUnits",
            "outputTaxCurrencyCode",
            "outputTaxMinorUnits",
            "recoverableInputTaxCurrencyCode",
            "recoverableInputTaxMinorUnits",
            "nonrecoverableInputTaxCurrencyCode",
            "nonrecoverableInputTaxMinorUnits",
            "netPayableCurrencyCode",
            "netPayableMinorUnits",
            "netReceivableCurrencyCode",
            "netReceivableMinorUnits"),
        rows);
  }
}
