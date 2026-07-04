package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Renders operator-facing text for reporting-period close operations. */
final class CliPeriodCloseOutputRenderer {
  private CliPeriodCloseOutputRenderer() {}

  static String renderSweptInterimResultText(SweptInterimResult sweptInterimResult) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Sweep order", Integer.toString(sweptInterimResult.sweepOrder())));
    rows.add(
        List.of(
            CliTemporalScopeText.summaryLabel(OperationId.INTERIM_RESULT_SWEEP),
            sweptInterimResult.reportingPeriod().effectiveDateFrom()
                + " to "
                + sweptInterimResult.reportingPeriod().effectiveDateTo()));
    rows.add(
        List.of("Result-holding account", sweptInterimResult.resultHoldingAccountCode().value()));
    rows.add(
        List.of(
            "Swept totals",
            CliBalanceOutputFormatter.joinedBalances(sweptInterimResult.sweptTotals())));
    rows.add(List.of("Swept at", CliTextDisplay.instant(sweptInterimResult.sweptAt())));
    rows.add(
        List.of(
            generatedPostingLabel(OperationId.INTERIM_RESULT_SWEEP),
            sweptInterimResult.sweepPostingIds().isEmpty()
                ? "(none)"
                : sweptInterimResult.sweepPostingIds().stream()
                    .map(dev.erst.fingrind.core.PostingId::value)
                    .collect(java.util.stream.Collectors.joining(", "))));
    if (sweptInterimResult.sweptTotals().isEmpty()
        && sweptInterimResult.sweepPostingIds().isEmpty()) {
      rows.add(
          List.of(
              "Outcome", "No closing movements were required for the selected reporting period."));
    }
    return CliTextFormat.renderTitledBlock(
        "Interim Result Swept", CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  static String renderClosedFiscalYearText(
      ClosedFiscalYear closedFiscalYear, boolean idempotentReplay) {
    List<List<String>> rows = new java.util.ArrayList<>();
    rows.add(List.of("Close order", Integer.toString(closedFiscalYear.closeOrder())));
    rows.add(
        List.of(
            CliTemporalScopeText.summaryLabel(OperationId.FISCAL_YEAR_CLOSE),
            closedFiscalYear.reportingPeriod().effectiveDateFrom()
                + " to "
                + closedFiscalYear.reportingPeriod().effectiveDateTo()));
    rows.add(List.of("Capital account", closedFiscalYear.capitalAccountCode().value()));
    rows.add(
        List.of("Result-holding account", closedFiscalYear.resultHoldingAccountCode().value()));
    rows.add(
        List.of(
            "Retained accumulated account",
            closedFiscalYear.retainedAccumulatedAccountCode().value()));
    rows.add(List.of("Closed at", CliTextDisplay.instant(closedFiscalYear.closedAt())));
    rows.add(
        List.of(
            generatedPostingLabel(OperationId.FISCAL_YEAR_CLOSE),
            closedFiscalYear.closePostingIds().isEmpty()
                ? "(none)"
                : closedFiscalYear.closePostingIds().stream()
                    .map(dev.erst.fingrind.core.PostingId::value)
                    .collect(java.util.stream.Collectors.joining(", "))));
    rows.add(List.of("Idempotent replay", CliQueryScopeText.displayBooleanLabel(idempotentReplay)));
    return CliTextFormat.renderTitledBlock(
        idempotentReplay ? "Fiscal Year Already Closed" : "Fiscal Year Closed",
        CliTextFormat.renderKeyValueBlock(List.copyOf(rows)));
  }

  private static String generatedPostingLabel(OperationId operationId) {
    return "Generated " + operationId.wireName() + " postings";
  }
}
