package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import java.util.List;

/** Renders account-ledger text and CSV outputs. */
final class CliAccountLedgerReportRenderer {
  private CliAccountLedgerReportRenderer() {}

  static String renderText(AccountLedgerReport report) {
    String entries =
        report.entries().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("ledger entries")
            : CliTextFormat.renderTable(
                List.of(
                    "Effective date",
                    "Origin",
                    "Debit",
                    "Credit",
                    "Running",
                    "Counterparts",
                    "Posting"),
                report.entries().stream()
                    .map(
                        entry ->
                            CliPostingFactFormatter.accountLedgerTextRow(report.account(), entry))
                    .toList(),
                2,
                3);
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Account",
                    report.account().accountCode().value()
                        + " "
                        + report.account().accountName().value()),
                List.of(
                    "Range",
                    CliQueryScopeText.dateRange(
                        report.effectiveDateRange().effectiveDateFrom().orElse(null),
                        report.effectiveDateRange().effectiveDateTo().orElse(null))),
                List.of(
                    "Opening balances",
                    CliBalanceOutputFormatter.joinedBalances(report.openingBalances())),
                List.of(
                    "Closing balances",
                    CliBalanceOutputFormatter.joinedBalances(report.closingBalances())),
                List.of(
                    "Outcome",
                    report.entries().isEmpty()
                        ? CliQueryScopeText.noMatchesLabel("ledger entries")
                        : Integer.toString(report.entries().size()) + " ledger entries")));
    String context =
        CliTextFormat.renderKeyValueBlock(
            CliReportRenderSupport.identityRows(
                report.bookIdentity(),
                report.postingCoverage(),
                List.of(
                    List.of(
                        "Account type",
                        CliAccountStatementLabels.displayLineTypeLabel(
                            report.account().accountType())),
                    List.of(
                        "Account role",
                        CliAccountStatementLabels.displayAccountRoleLabel(
                            report.account().accountRole())),
                    List.of(
                        "Normal balance",
                        CliAccountStatementLabels.displayNormalBalanceLabel(
                            report.account().normalBalance())),
                    List.of(
                        "Active",
                        CliQueryScopeText.displayBooleanLabel(report.account().active())))));
    return CliTextFormat.renderTitledBlock(
        "Account Ledger",
        CliReportRenderSupport.joinSections(
            CliReportRenderSupport.section("Entries", entries),
            summary,
            CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(AccountLedgerReport report) {
    return CliAccountLedgerCsvRenderer.render(report);
  }
}
