package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Renders period-summary text and CSV outputs. */
final class CliPeriodSummaryReportRenderer {
  private CliPeriodSummaryReportRenderer() {}

  static String renderText(PeriodSummaryReport report) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    CliTemporalScopeText.lowerLabel(OperationId.PERIOD_SUMMARY),
                    report.effectiveDateFrom().toString()),
                List.of(
                    CliTemporalScopeText.upperLabel(OperationId.PERIOD_SUMMARY),
                    report.effectiveDateTo().toString()),
                List.of("Posting count", Integer.toString(report.postingCount())),
                List.of("Posting line count", Integer.toString(report.postingLineCount())),
                List.of("Accounts touched", Integer.toString(report.accountsTouched()))));
    String currencyTotals =
        report.currencyTotals().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("currency totals")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
                report.currencyTotals().stream()
                    .map(
                        currencySummary ->
                            CliBalanceOutputFormatter.balanceTextRow(currencySummary.totals()))
                    .toList(),
                1,
                2,
                3);
    String accountActivity =
        report.accountActivity().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("account activity")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of(
                    "Account",
                    "Name",
                    "Currency",
                    "Debit total",
                    "Credit total",
                    "Net amount",
                    "Balance side"),
                report.accountActivity().stream()
                    .map(
                        row ->
                            List.of(
                                row.account().accountCode().value(),
                                row.account().accountName().value(),
                                row.movement().netAmount().currencyUnit().code(),
                                CliQueryScopeText.displayMoney(row.movement().debitTotal()),
                                CliQueryScopeText.displayMoney(row.movement().creditTotal()),
                                CliQueryScopeText.displayMoney(row.movement().netAmount()),
                                CliBalanceOutputFormatter.displayBalanceSideLabel(
                                    row.movement().balanceSide())))
                    .toList(),
                3,
                4,
                5);
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(), report.postingCoverage(), List.of()));
    return CliTextFormat.renderTitledBlock(
        "Period Summary",
        CliReportRenderSupport.joinSections(
            summary,
            CliReportRenderSupport.section("Currency totals", currencyTotals),
            CliReportRenderSupport.section("Account activity", accountActivity),
            CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(PeriodSummaryReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "recordKind",
            "subjectKind",
            "subjectCode",
            "subjectName",
            "metricName",
            "metricValue",
            "currencyCode",
            "metricUnit",
            "message"),
        CliPeriodSummaryCsvRows.rows(report));
  }
}
