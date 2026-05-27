package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;

/** Shared balance-table rendering support for PDF reports. */
final class PdfBalanceTableSupport {
  private PdfBalanceTableSupport() {}

  static void writeSummaryTable(
      PdfPageWriter pageWriter, String heading, List<CurrencyBalance> balances) throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.currencyBalanceSummaryColumns(),
        balances.stream().map(PdfBalanceTableSupport::summaryRow).toList());
  }

  static void writeDetailedTable(
      PdfPageWriter pageWriter, String heading, List<CurrencyBalance> balances) throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.detailedCurrencyBalanceColumns(),
        balances.stream().map(PdfBalanceTableSupport::detailedRow).toList());
  }

  static List<String> summaryRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(balance.debitTotal()),
        PdfValueFormatter.displayMoney(balance.creditTotal()),
        PdfValueFormatter.displayMoney(balance.netAmount()),
        PdfValueFormatter.displayBalanceSide(balance.balanceSide()));
  }

  static List<String> detailedRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(balance.debitTotal()),
        PdfValueFormatter.displayMoney(balance.creditTotal()),
        PdfValueFormatter.displayMoney(balance.netAmount()),
        PdfValueFormatter.displayBalanceSide(balance.balanceSide()));
  }
}
