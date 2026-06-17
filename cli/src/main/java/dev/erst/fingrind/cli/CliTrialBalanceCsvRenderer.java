package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders trial-balance CSV exports under the shared comparative-surface policy. */
final class CliTrialBalanceCsvRenderer {
  private CliTrialBalanceCsvRenderer() {}

  static String render(TrialBalanceReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateAsOf",
            "balanced",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
            "normalBalance",
            "active",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        java.util.stream.Stream.concat(currentRows(report), comparativeRows(report)).toList());
  }

  private static java.util.stream.Stream<List<String>> currentRows(TrialBalanceReport report) {
    String effectiveDateAsOf =
        report.resolvedEffectiveDateAsOf().map(LocalDate::toString).orElse("");
    return java.util.stream.Stream.concat(
        report.rows().stream().map(row -> detailRow("current", effectiveDateAsOf, row)),
        java.util.stream.Stream.concat(
            report.totals().stream()
                .map(total -> totalRow("current", effectiveDateAsOf, report.balanced(), total)),
            emptyCurrentRows(report, effectiveDateAsOf)));
  }

  private static java.util.stream.Stream<List<String>> comparativeRows(TrialBalanceReport report) {
    if (!CliReportSurfacePolicy.hasComparative(report)) {
      return java.util.stream.Stream.empty();
    }
    String effectiveDateAsOf =
        report
            .comparativeEffectiveDateRange()
            .effectiveDateTo()
            .map(LocalDate::toString)
            .orElse("");
    return java.util.stream.Stream.concat(
        report.comparativeRows().stream()
            .map(row -> detailRow("comparative", effectiveDateAsOf, row)),
        java.util.stream.Stream.concat(
            report.comparativeTotals().stream()
                .map(
                    total ->
                        totalRow(
                            "comparative", effectiveDateAsOf, report.comparativeBalanced(), total)),
            emptyComparativeRows(report, effectiveDateAsOf)));
  }

  private static java.util.stream.Stream<List<String>> emptyCurrentRows(
      TrialBalanceReport report, String effectiveDateAsOf) {
    List<List<String>> rows = new ArrayList<>();
    if (!CliReportSurfacePolicy.hasCurrent(report)) {
      rows.add(
          emptyRow(
              "current",
              effectiveDateAsOf,
              report.bookIdentity().functionalCurrency().code(),
              CliQueryScopeText.noMatchesLabel("account balances")));
    }
    return rows.stream();
  }

  private static java.util.stream.Stream<List<String>> emptyComparativeRows(
      TrialBalanceReport report, String effectiveDateAsOf) {
    List<List<String>> rows = new ArrayList<>();
    if (!CliReportSurfacePolicy.hasComparativeData(report)) {
      rows.add(
          emptyRow(
              "comparative",
              effectiveDateAsOf,
              report.bookIdentity().functionalCurrency().code(),
              CliQueryScopeText.noMatchesLabel("account balances")));
    }
    return rows.stream();
  }

  private static List<String> emptyRow(
      String reportBasis, String effectiveDateAsOf, String currencyCode, String message) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "trial-balance-empty:" + reportBasis + ":" + effectiveDateAsOf,
        "",
        "report-empty",
        reportBasis,
        CliCsvEmptyKinds.REPORT_EMPTY,
        effectiveDateAsOf,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        currencyCode,
        "",
        "",
        "",
        "",
        message);
  }

  private static List<String> detailRow(
      String reportBasis, String effectiveDateAsOf, TrialBalanceRow row) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "trial-balance-row:" + reportBasis + ":" + row.account().accountCode().value(),
        "",
        "line",
        reportBasis,
        "row",
        effectiveDateAsOf,
        "",
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().accountRole().wireValue(),
        row.account().normalBalance().wireValue(),
        Boolean.toString(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(row.balance().debitTotal()),
        CliQueryScopeText.displayMoney(row.balance().creditTotal()),
        CliQueryScopeText.displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue(),
        "");
  }

  private static List<String> totalRow(
      String reportBasis, String effectiveDateAsOf, boolean balanced, CurrencyBalance total) {
    return List.of(
        CliCsvExportFamilies.STATEMENT,
        "trial-balance-total:" + reportBasis + ":" + total.netAmount().currencyUnit().code(),
        "",
        "report-total",
        reportBasis,
        "total",
        effectiveDateAsOf,
        Boolean.toString(balanced),
        "",
        "",
        "",
        "",
        "",
        "",
        total.netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(total.debitTotal()),
        CliQueryScopeText.displayMoney(total.creditTotal()),
        CliQueryScopeText.displayMoney(total.netAmount()),
        total.balanceSide().wireValue(),
        "");
  }
}
