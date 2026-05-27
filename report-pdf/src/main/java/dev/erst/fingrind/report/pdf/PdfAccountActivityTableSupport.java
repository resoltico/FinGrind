package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import java.io.IOException;
import java.util.List;

/** Shared account-activity table rendering for trial-balance and period-summary PDFs. */
final class PdfAccountActivityTableSupport {
  private PdfAccountActivityTableSupport() {}

  static void writeTrialBalanceTable(
      PdfPageWriter pageWriter, String heading, List<TrialBalanceRow> rows) throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.accountActivityColumns(),
        rows.stream().map(PdfAccountActivityTableSupport::trialBalanceRow).toList());
  }

  static void writePeriodAccountActivityTable(
      PdfPageWriter pageWriter, String heading, List<PeriodAccountActivityRow> rows)
      throws IOException {
    pageWriter.writeTable(
        heading,
        PdfReportTableLayouts.accountActivityColumns(),
        rows.stream().map(PdfAccountActivityTableSupport::periodAccountActivityRow).toList());
  }

  private static List<String> trialBalanceRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        PdfValueFormatter.displayAccountType(row.account().accountType()),
        PdfValueFormatter.displayAccountRole(row.account().accountRole()),
        PdfValueFormatter.displayNormalBalance(row.account().normalBalance()),
        PdfValueFormatter.displayBoolean(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.balance().debitTotal()),
        PdfValueFormatter.displayMoney(row.balance().creditTotal()),
        PdfValueFormatter.displayMoney(row.balance().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.balance().balanceSide()));
  }

  private static List<String> periodAccountActivityRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        PdfValueFormatter.displayAccountType(row.account().accountType()),
        PdfValueFormatter.displayAccountRole(row.account().accountRole()),
        PdfValueFormatter.displayNormalBalance(row.account().normalBalance()),
        PdfValueFormatter.displayBoolean(row.account().active()),
        row.movement().netAmount().currencyUnit().code(),
        PdfValueFormatter.displayMoney(row.movement().debitTotal()),
        PdfValueFormatter.displayMoney(row.movement().creditTotal()),
        PdfValueFormatter.displayMoney(row.movement().netAmount()),
        PdfValueFormatter.displayBalanceSide(row.movement().balanceSide()));
  }
}
