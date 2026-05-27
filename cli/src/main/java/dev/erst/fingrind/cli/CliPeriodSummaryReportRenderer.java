package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import java.util.List;

/** Renders period-summary text and CSV outputs. */
final class CliPeriodSummaryReportRenderer {
  private CliPeriodSummaryReportRenderer() {}

  static String renderText(PeriodSummaryReport report) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Range", report.effectiveDateFrom() + " to " + report.effectiveDateTo()),
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
            "recordKind",
            "subjectKind",
            "subjectCode",
            "subjectName",
            "metricName",
            "metricValue",
            "currencyCode",
            "metricUnit",
            "message"),
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "postingCount",
                        Integer.toString(report.postingCount()),
                        "",
                        "count",
                        ""),
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "postingLineCount",
                        Integer.toString(report.postingLineCount()),
                        "",
                        "count",
                        ""),
                    List.of(
                        "summary",
                        "book",
                        "",
                        "",
                        "accountsTouched",
                        Integer.toString(report.accountsTouched()),
                        "",
                        "count",
                        "")),
                java.util.stream.Stream.concat(
                    report.currencyTotals().isEmpty()
                        ? java.util.stream.Stream.of(
                            List.of(
                                "empty",
                                "currency",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                CliQueryScopeText.noMatchesLabel("currency totals")))
                        : report.currencyTotals().stream()
                            .flatMap(
                                summary ->
                                    java.util.stream.Stream.of(
                                        List.of(
                                            "currency-total",
                                            "currency",
                                            summary.totals().netAmount().currencyUnit().code(),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "debitTotal",
                                            CliQueryScopeText.displayMoney(
                                                summary.totals().debitTotal()),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "currency-total",
                                            "currency",
                                            summary.totals().netAmount().currencyUnit().code(),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "creditTotal",
                                            CliQueryScopeText.displayMoney(
                                                summary.totals().creditTotal()),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "currency-total",
                                            "currency",
                                            summary.totals().netAmount().currencyUnit().code(),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "netAmount",
                                            CliQueryScopeText.displayMoney(
                                                summary.totals().netAmount()),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "currency-total",
                                            "currency",
                                            summary.totals().netAmount().currencyUnit().code(),
                                            summary.totals().netAmount().currencyUnit().code(),
                                            "balanceSide",
                                            summary.totals().balanceSide().wireValue(),
                                            "",
                                            "enum",
                                            ""))),
                    report.accountActivity().isEmpty()
                        ? java.util.stream.Stream.of(
                            List.of(
                                "empty",
                                "account",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                CliQueryScopeText.noMatchesLabel("account activity")))
                        : report.accountActivity().stream()
                            .flatMap(
                                row ->
                                    java.util.stream.Stream.of(
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "accountType",
                                            row.account().accountType().wireValue(),
                                            "",
                                            "enum",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "accountRole",
                                            row.account().accountRole().wireValue(),
                                            "",
                                            "enum",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "normalBalance",
                                            row.account().normalBalance().wireValue(),
                                            "",
                                            "enum",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "active",
                                            Boolean.toString(row.account().active()),
                                            "",
                                            "flag",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "declaredAt",
                                            row.account().declaredAt().toString(),
                                            "",
                                            "timestamp",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "debitTotal",
                                            CliQueryScopeText.displayMoney(
                                                row.movement().debitTotal()),
                                            row.movement().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "creditTotal",
                                            CliQueryScopeText.displayMoney(
                                                row.movement().creditTotal()),
                                            row.movement().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "netAmount",
                                            CliQueryScopeText.displayMoney(
                                                row.movement().netAmount()),
                                            row.movement().netAmount().currencyUnit().code(),
                                            "money",
                                            ""),
                                        List.of(
                                            "account-activity",
                                            "account",
                                            row.account().accountCode().value(),
                                            row.account().accountName().value(),
                                            "balanceSide",
                                            row.movement().balanceSide().wireValue(),
                                            "",
                                            "enum",
                                            "")))))
            .toList());
  }
}
