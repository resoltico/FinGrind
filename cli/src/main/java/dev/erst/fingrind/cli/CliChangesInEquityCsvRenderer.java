package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.time.LocalDate;
import java.util.List;

/** Renders changes-in-equity CSV exports apart from the human-focused text view. */
final class CliChangesInEquityCsvRenderer {
  private CliChangesInEquityCsvRenderer() {}

  static String renderCsv(ChangesInEquityReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
            "lineCode",
            "lineName",
            "lineRole",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "openingDebitTotal",
            "openingCreditTotal",
            "openingNetAmount",
            "openingBalanceSide",
            "movementDebitTotal",
            "movementCreditTotal",
            "movementNetAmount",
            "movementBalanceSide",
            "closingDebitTotal",
            "closingCreditTotal",
            "closingNetAmount",
            "closingBalanceSide",
            "message"),
        (CliReportSurfacePolicy.hasComparative(report)
                ? java.util.stream.Stream.concat(
                    csvRows(
                        report,
                        "current",
                        report.rows(),
                        report.openingTotals(),
                        report.movementTotals(),
                        report.closingTotals()),
                    csvRows(
                        report,
                        "comparative",
                        report.comparativeRows(),
                        report.comparativeOpeningTotals(),
                        report.comparativeMovementTotals(),
                        report.comparativeClosingTotals()))
                : csvRows(
                    report,
                    "current",
                    report.rows(),
                    report.openingTotals(),
                    report.movementTotals(),
                    report.closingTotals()))
            .toList());
  }

  private static java.util.stream.Stream<List<String>> csvRows(
      ChangesInEquityReport report,
      String reportBasis,
      List<ChangesInEquityRow> rows,
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    java.util.stream.Stream<List<String>> rowStream =
        rows.stream()
            .map(
                row ->
                    List.of(
                        CliCsvExportFamilies.STATEMENT,
                        "changes-in-equity-row:" + reportBasis + ":" + row.lineCode(),
                        "",
                        "line",
                        reportBasis,
                        "row",
                        effectiveDateFrom,
                        effectiveDateTo,
                        row.lineCode(),
                        row.lineName(),
                        row.lineRole().map(AccountRole::wireValue).orElse(""),
                        row.lineClassification()
                            .map(FinancialPositionLineClassification::wireValue)
                            .orElse(""),
                        row.lineKind().wireValue(),
                        row.closingBalance().netAmount().currencyUnit().code(),
                        CliQueryScopeText.displayMoney(row.openingBalance().debitTotal()),
                        CliQueryScopeText.displayMoney(row.openingBalance().creditTotal()),
                        CliQueryScopeText.displayMoney(row.openingBalance().netAmount()),
                        row.openingBalance().balanceSide().wireValue(),
                        CliQueryScopeText.displayMoney(row.movement().debitTotal()),
                        CliQueryScopeText.displayMoney(row.movement().creditTotal()),
                        CliQueryScopeText.displayMoney(row.movement().netAmount()),
                        row.movement().balanceSide().wireValue(),
                        CliQueryScopeText.displayMoney(row.closingBalance().debitTotal()),
                        CliQueryScopeText.displayMoney(row.closingBalance().creditTotal()),
                        CliQueryScopeText.displayMoney(row.closingBalance().netAmount()),
                        row.closingBalance().balanceSide().wireValue(),
                        ""));
    java.util.stream.Stream<List<String>> totalStream =
        totalCsvRows(report, reportBasis, openingTotals, movementTotals, closingTotals);
    List<List<String>> renderedRows =
        java.util.stream.Stream.concat(rowStream, totalStream).toList();
    if (!renderedRows.isEmpty()) {
      return renderedRows.stream();
    }
    return java.util.stream.Stream.of(
        List.of(
            CliCsvExportFamilies.STATEMENT,
            "changes-in-equity-report-empty:"
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
            report.bookIdentity().functionalCurrency().code(),
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
            "",
            "",
            CliQueryScopeText.noMatchesLabel("equity lines")));
  }

  private static java.util.stream.Stream<List<String>> totalCsvRows(
      ChangesInEquityReport report,
      String reportBasis,
      List<CurrencyBalance> openingTotals,
      List<CurrencyBalance> movementTotals,
      List<CurrencyBalance> closingTotals) {
    String effectiveDateFrom =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateFrom().toString();
    String effectiveDateTo =
        "comparative".equals(reportBasis)
            ? report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse("")
            : report.effectiveDateTo().toString();
    List<String> currencyCodes =
        java.util.stream.Stream.of(openingTotals, movementTotals, closingTotals)
            .flatMap(List::stream)
            .map(total -> total.netAmount().currencyUnit().code())
            .distinct()
            .toList();
    return currencyCodes.stream()
        .map(
            currencyCode -> {
              CurrencyBalance opening =
                  CliReportRenderSupport.balanceForCurrency(openingTotals, currencyCode);
              CurrencyBalance movement =
                  CliReportRenderSupport.balanceForCurrency(movementTotals, currencyCode);
              CurrencyBalance closing =
                  CliReportRenderSupport.balanceForCurrency(closingTotals, currencyCode);
              return List.of(
                  CliCsvExportFamilies.STATEMENT,
                  "changes-in-equity-total:"
                      + reportBasis
                      + ":"
                      + currencyCode
                      + ":"
                      + closing.balanceSide().wireValue(),
                  "",
                  "report-total",
                  reportBasis,
                  "report-total",
                  effectiveDateFrom,
                  effectiveDateTo,
                  "report-total",
                  "Report total",
                  "",
                  "",
                  "REPORT_TOTAL",
                  currencyCode,
                  CliQueryScopeText.displayMoney(opening.debitTotal()),
                  CliQueryScopeText.displayMoney(opening.creditTotal()),
                  CliQueryScopeText.displayMoney(opening.netAmount()),
                  opening.balanceSide().wireValue(),
                  CliQueryScopeText.displayMoney(movement.debitTotal()),
                  CliQueryScopeText.displayMoney(movement.creditTotal()),
                  CliQueryScopeText.displayMoney(movement.netAmount()),
                  movement.balanceSide().wireValue(),
                  CliQueryScopeText.displayMoney(closing.debitTotal()),
                  CliQueryScopeText.displayMoney(closing.creditTotal()),
                  CliQueryScopeText.displayMoney(closing.netAmount()),
                  closing.balanceSide().wireValue(),
                  "");
            });
  }
}
