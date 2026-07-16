package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.List;

/** Renders the human account-balance report projection. */
final class CliAccountBalanceOutputRenderer {
  private CliAccountBalanceOutputRenderer() {}

  static String renderText(AccountBalanceSnapshot snapshot) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Account",
                    CliHumanDisplay.accountLabel(
                        snapshot.account().accountCode().value(),
                        snapshot.account().accountName().value())),
                List.of(
                    CliTemporalScopeText.summaryLabel(OperationId.ACCOUNT_BALANCE),
                    CliQueryScopeText.dateRange(
                        snapshot.effectiveDateFrom().orElse(null),
                        snapshot.effectiveDateTo().orElse(null)))));
    String balances =
        snapshot.balances().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("balances")
            : CliTextFormat.renderTable(
                List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
                snapshot.balances().stream()
                    .map(CliBalanceOutputFormatter::balanceTextRow)
                    .toList(),
                1,
                2,
                3);
    String context =
        CliTextFormat.renderKeyValueBlock(
            mergeContextRows(
                CliBookIdentityDisplay.contextRows(snapshot.bookIdentity()),
                List.of(
                    List.of(
                        "Posting coverage",
                        CliPostingLabels.displayPostingCoverage(snapshot.postingCoverage())),
                    List.of(
                        "Account type",
                        CliAccountStatementLabels.displayLineTypeLabel(
                            snapshot.account().accountType())),
                    List.of(
                        "Normal balance",
                        CliAccountStatementLabels.displayNormalBalanceLabel(
                            snapshot.account().normalBalance())),
                    List.of(
                        "Active",
                        CliQueryScopeText.displayBooleanLabel(snapshot.account().active())))));
    return CliTextFormat.renderTitledBlock(
        "Account Balance",
        CliReportRenderSupport.joinSections(
            summary, balances, CliReportRenderSupport.section("Context", context)));
  }

  private static List<List<String>> mergeContextRows(
      List<List<String>> firstRows, List<List<String>> secondRows) {
    List<List<String>> rows = new java.util.ArrayList<>(firstRows);
    rows.addAll(secondRows);
    return List.copyOf(rows);
  }
}
