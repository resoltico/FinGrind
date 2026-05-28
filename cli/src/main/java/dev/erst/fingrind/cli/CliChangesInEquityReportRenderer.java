package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Renders changes-in-equity text and CSV outputs. */
final class CliChangesInEquityReportRenderer {
  private CliChangesInEquityReportRenderer() {}

  static String renderText(ChangesInEquityReport report) {
    boolean hasCurrent = CliReportSurfacePolicy.hasCurrent(report);
    String summary = CliTextFormat.renderKeyValueBlock(summaryRows(report, hasCurrent));
    String table =
        report.rows().isEmpty()
            ? ""
            : CliReportRenderSupport.section("Equity lines", renderTable(report.rows()));
    String comparative =
        CliReportSurfacePolicy.hasComparative(report)
            ? CliReportRenderSupport.section(
                "Comparative Changes In Equity",
                CliReportRenderSupport.joinSections(
                    CliTextFormat.renderKeyValueBlock(comparativeSummaryRows(report)),
                    report.comparativeRows().isEmpty()
                        ? ""
                        : CliReportRenderSupport.section(
                            "Equity lines", renderTable(report.comparativeRows()))))
            : "";
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Changes In Equity",
        CliReportRenderSupport.joinSections(
            summary, table, comparative, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(ChangesInEquityReport report) {
    return CliTextFormat.renderCsv(
        List.of(
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
        java.util.stream.Stream.concat(
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
            .toList());
  }

  private static List<List<String>> summaryRows(ChangesInEquityReport report, boolean hasCurrent) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Effective date from", report.effectiveDateFrom().toString()));
    rows.add(List.of("Effective date to", report.effectiveDateTo().toString()));
    if (!report.openingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Opening totals", CliReportRenderSupport.joinedBalancesText(report.openingTotals())));
    }
    if (!report.movementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Movement totals",
              CliReportRenderSupport.joinedBalancesText(report.movementTotals())));
    }
    if (!report.closingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Closing totals", CliReportRenderSupport.joinedBalancesText(report.closingTotals())));
    }
    if (!hasCurrent) {
      rows.add(List.of("Outcome", CliQueryScopeText.noMatchesLabel("equity lines")));
    }
    return List.copyOf(rows);
  }

  private static String renderTable(List<ChangesInEquityRow> rows) {
    return CliTextFormat.renderAdaptiveTable(
        CliReportRenderSupport.TEXT_TABLE_WIDTH,
        List.of("Line code", "Line name", "Opening", "Movement", "Closing", "Closing side"),
        rows.stream()
            .map(
                row ->
                    List.of(
                        CliAccountStatementLabels.displayStatementLineCode(
                            row.lineCode(), row.lineKind()),
                        row.lineName(),
                        CliQueryScopeText.displayMoney(row.openingBalance().netAmount()),
                        CliQueryScopeText.displayMoney(row.movement().netAmount()),
                        CliQueryScopeText.displayMoney(row.closingBalance().netAmount()),
                        CliBalanceOutputFormatter.displayBalanceSideLabel(
                            row.closingBalance().balanceSide())))
            .toList(),
        2,
        3);
  }

  private static List<List<String>> comparativeSummaryRows(ChangesInEquityReport report) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(
        List.of(
            "Comparative reference",
            CliReportRenderSupport.comparativeReferenceLine(
                report.comparativeEffectiveDateRange())));
    rows.add(
        List.of(
            "Effective date from",
            report
                .comparativeEffectiveDateRange()
                .effectiveDateFrom()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateFrom().toString())));
    rows.add(
        List.of(
            "Effective date to",
            report
                .comparativeEffectiveDateRange()
                .effectiveDateTo()
                .map(LocalDate::toString)
                .orElse(report.effectiveDateTo().toString())));
    if (!report.comparativeOpeningTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative opening totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeOpeningTotals())));
    }
    if (!report.comparativeMovementTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative movement totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeMovementTotals())));
    }
    if (!report.comparativeClosingTotals().isEmpty()) {
      rows.add(
          List.of(
              "Comparative closing totals",
              CliReportRenderSupport.joinedBalancesText(report.comparativeClosingTotals())));
    }
    return List.copyOf(rows);
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
