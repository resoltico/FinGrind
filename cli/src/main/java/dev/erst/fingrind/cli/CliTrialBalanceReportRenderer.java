package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;

/** Renders trial-balance text and CSV outputs. */
final class CliTrialBalanceReportRenderer {
  private CliTrialBalanceReportRenderer() {}

  static String renderText(TrialBalanceReport report) {
    boolean hasComparative = CliReportSurfacePolicy.hasComparative(report);
    boolean hasCurrent = CliReportSurfacePolicy.hasCurrent(report);
    String summary =
        CliTextFormat.renderKeyValueBlock(
            hasCurrent
                ? List.of(
                    List.of(
                        "As of",
                        CliQueryScopeText.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null))),
                    List.of(
                        "Balance state",
                        CliBalanceOutputFormatter.displayBalanceStateLabel(report.balanced())))
                : List.of(
                    List.of(
                        "As of",
                        CliQueryScopeText.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null))),
                    List.of("Outcome", CliQueryScopeText.noMatchesLabel("account balances"))));
    String totals =
        hasCurrent
            ? CliReportRenderSupport.section("Current totals", renderTotalsTable(report.totals()))
            : "";
    String table =
        !report.rows().isEmpty()
            ? CliReportRenderSupport.section(
                "Accounts",
                CliTextFormat.renderAdaptiveTable(
                    CliReportRenderSupport.TEXT_TABLE_WIDTH,
                    List.of(
                        "Account",
                        "Name",
                        "Currency",
                        "Debit total",
                        "Credit total",
                        "Net amount",
                        "Balance side"),
                    report.rows().stream().map(CliTrialBalanceReportRenderer::textRow).toList(),
                    3,
                    4,
                    5))
            : "";
    String comparative =
        !hasComparative
            ? ""
            : CliReportRenderSupport.section(
                "Comparative Trial Balance",
                CliReportRenderSupport.comparativeReferenceLine(
                        report.comparativeEffectiveDateRange())
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + CliTextFormat.renderKeyValueBlock(
                        List.of(
                            List.of(
                                "As of",
                                CliQueryScopeText.upperDateBoundaryLabel(
                                    report
                                        .comparativeEffectiveDateRange()
                                        .effectiveDateTo()
                                        .orElse(null))),
                            List.of(
                                "Balance state",
                                CliBalanceOutputFormatter.displayBalanceStateLabel(
                                    report.comparativeBalanced()))))
                    + System.lineSeparator()
                    + System.lineSeparator()
                    + renderTotalsTable(report.comparativeTotals())
                    + (report.comparativeRows().isEmpty()
                        ? ""
                        : System.lineSeparator()
                            + System.lineSeparator()
                            + CliReportRenderSupport.section(
                                "Accounts",
                                CliTextFormat.renderAdaptiveTable(
                                    CliReportRenderSupport.TEXT_TABLE_WIDTH,
                                    List.of(
                                        "Account",
                                        "Name",
                                        "Currency",
                                        "Debit total",
                                        "Credit total",
                                        "Net amount",
                                        "Balance side"),
                                    report.comparativeRows().stream()
                                        .map(CliTrialBalanceReportRenderer::textRow)
                                        .toList(),
                                    3,
                                    4,
                                    5))));
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Trial Balance",
        CliReportRenderSupport.joinSections(
            summary,
            totals,
            table,
            comparative,
            CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(TrialBalanceReport report) {
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
        java.util.stream.Stream.of(
                report.rows().stream()
                    .map(
                        row ->
                            csvRow(
                                "current",
                                report.effectiveDateAsOf().map(LocalDate::toString).orElse(""),
                                row)),
                report.totals().stream()
                    .map(
                        total ->
                            totalCsvRow(
                                "current",
                                report.effectiveDateAsOf().map(LocalDate::toString).orElse(""),
                                report.balanced(),
                                total)),
                report.comparativeRows().stream()
                    .map(
                        row ->
                            csvRow(
                                "comparative",
                                report
                                    .comparativeEffectiveDateRange()
                                    .effectiveDateTo()
                                    .map(LocalDate::toString)
                                    .orElse(""),
                                row)),
                report.comparativeTotals().stream()
                    .map(
                        total ->
                            totalCsvRow(
                                "comparative",
                                report
                                    .comparativeEffectiveDateRange()
                                    .effectiveDateTo()
                                    .map(LocalDate::toString)
                                    .orElse(""),
                                report.comparativeBalanced(),
                                total)),
                emptyCsvRows(report))
            .flatMap(stream -> stream)
            .toList());
  }

  private static List<String> textRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.balance().netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(row.balance().debitTotal()),
        CliQueryScopeText.displayMoney(row.balance().creditTotal()),
        CliQueryScopeText.displayMoney(row.balance().netAmount()),
        CliBalanceOutputFormatter.displayBalanceSideLabel(row.balance().balanceSide()));
  }

  private static String renderTotalsTable(List<CurrencyBalance> totals) {
    if (totals.isEmpty()) {
      return CliQueryScopeText.noMatchesLabel("balances");
    }
    return CliTextFormat.renderTable(
        List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
        totals.stream()
            .map(
                total ->
                    List.of(
                        total.netAmount().currencyUnit().code(),
                        CliQueryScopeText.displayMoney(total.debitTotal()),
                        CliQueryScopeText.displayMoney(total.creditTotal()),
                        CliQueryScopeText.displayMoney(total.netAmount()),
                        CliBalanceOutputFormatter.displayBalanceSideLabel(total.balanceSide())))
            .toList(),
        1,
        2,
        3);
  }

  private static java.util.stream.Stream<List<String>> emptyCsvRows(TrialBalanceReport report) {
    java.util.List<List<String>> rows = new java.util.ArrayList<>();
    if (!CliReportSurfacePolicy.hasCurrent(report)) {
      rows.add(
          emptyCsvRow(
              "current",
              report.effectiveDateAsOf().map(LocalDate::toString).orElse(""),
              report.bookIdentity().functionalCurrency().code(),
              CliQueryScopeText.noMatchesLabel("account balances")));
    }
    if (CliReportSurfacePolicy.hasComparative(report) && report.comparativeRows().isEmpty()) {
      rows.add(
          emptyCsvRow(
              "comparative",
              report
                  .comparativeEffectiveDateRange()
                  .effectiveDateTo()
                  .map(LocalDate::toString)
                  .orElse(""),
              report.bookIdentity().functionalCurrency().code(),
              CliQueryScopeText.noMatchesLabel("account balances")));
    }
    return rows.stream();
  }

  private static List<String> emptyCsvRow(
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

  private static List<String> csvRow(
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

  private static List<String> totalCsvRow(
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
