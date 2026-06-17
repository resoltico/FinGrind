package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.List;

/** Renders trial-balance text and CSV outputs. */
final class CliTrialBalanceReportRenderer {
  private CliTrialBalanceReportRenderer() {}

  static String renderText(TrialBalanceReport report) {
    boolean hasComparative = CliReportSurfacePolicy.hasComparative(report);
    boolean hasComparativeData = CliReportSurfacePolicy.hasComparativeData(report);
    boolean hasCurrent = CliReportSurfacePolicy.hasCurrent(report);
    String summary =
        CliTextFormat.renderKeyValueBlock(
            hasCurrent
                ? List.of(
                    List.of(
                        CliTemporalScopeText.summaryLabel(OperationId.TRIAL_BALANCE),
                        CliQueryScopeText.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null),
                            report.resolvedEffectiveDateAsOf().orElse(null))),
                    List.of(
                        "Balance state",
                        CliBalanceOutputFormatter.displayBalanceStateLabel(report.balanced())))
                : List.of(
                    List.of(
                        CliTemporalScopeText.summaryLabel(OperationId.TRIAL_BALANCE),
                        CliQueryScopeText.upperDateBoundaryLabel(
                            report.effectiveDateAsOf().orElse(null),
                            report.resolvedEffectiveDateAsOf().orElse(null))),
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
                            hasComparativeData
                                ? List.of(
                                    List.of(
                                        CliTemporalScopeText.summaryLabel(
                                            OperationId.TRIAL_BALANCE),
                                        CliQueryScopeText.upperDateBoundaryLabel(
                                            report
                                                .comparativeEffectiveDateRange()
                                                .effectiveDateTo()
                                                .orElse(null))),
                                    List.of(
                                        "Balance state",
                                        CliBalanceOutputFormatter.displayBalanceStateLabel(
                                            report.comparativeBalanced())))
                                : List.of(
                                    List.of(
                                        CliTemporalScopeText.summaryLabel(
                                            OperationId.TRIAL_BALANCE),
                                        CliQueryScopeText.upperDateBoundaryLabel(
                                            report
                                                .comparativeEffectiveDateRange()
                                                .effectiveDateTo()
                                                .orElse(null))),
                                    List.of(
                                        "Outcome",
                                        CliQueryScopeText.noMatchesLabel("account balances")))))
                + (hasComparativeData
                    ? System.lineSeparator()
                        + System.lineSeparator()
                        + renderTotalsTable(report.comparativeTotals())
                    : "")
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
                                5)));
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
    return CliTrialBalanceCsvRenderer.render(report);
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
    if (totals.isEmpty()) return CliQueryScopeText.noMatchesLabel("balances");
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
}
