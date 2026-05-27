package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import java.time.LocalDate;
import java.util.List;

/** Renders account-balance query payloads for text and CSV output modes. */
final class CliAccountBalanceOutputRenderer {
  private CliAccountBalanceOutputRenderer() {}

  static String renderText(AccountBalanceSnapshot snapshot) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Account",
                    snapshot.account().accountCode().value()
                        + " "
                        + snapshot.account().accountName().value()),
                List.of(
                    "Range",
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
            List.of(
                List.of(
                    "Book",
                    CliBookIdentityDisplay.summaryRows(snapshot.bookIdentity()).getFirst().get(1)),
                List.of(
                    "Posting coverage",
                    CliPostingLabels.displayPostingCoverage(snapshot.postingCoverage())),
                List.of(
                    "Account type",
                    CliAccountStatementLabels.displayLineTypeLabel(
                        snapshot.account().accountType())),
                List.of(
                    "Account role",
                    CliAccountStatementLabels.displayAccountRoleLabel(
                        snapshot.account().accountRole())),
                List.of(
                    "Normal balance",
                    CliAccountStatementLabels.displayNormalBalanceLabel(
                        snapshot.account().normalBalance())),
                List.of(
                    "Active", CliQueryScopeText.displayBooleanLabel(snapshot.account().active()))));
    return CliTextFormat.renderTitledBlock(
        "Account Balance",
        CliReportRenderSupport.joinSections(
            summary, balances, CliReportRenderSupport.section("Context", context)));
  }

  static String renderCsv(AccountBalanceSnapshot snapshot) {
    return CliTextFormat.renderCsv(
        List.of(
            "recordKind",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
            "normalBalance",
            "effectiveDateFrom",
            "effectiveDateTo",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        snapshot.balances().isEmpty()
            ? List.of(
                List.of(
                    "empty",
                    snapshot.account().accountCode().value(),
                    snapshot.account().accountName().value(),
                    snapshot.account().accountType().wireValue(),
                    snapshot.account().accountRole().wireValue(),
                    snapshot.account().normalBalance().wireValue(),
                    snapshot.effectiveDateFrom().map(LocalDate::toString).orElse(""),
                    snapshot.effectiveDateTo().map(LocalDate::toString).orElse(""),
                    "",
                    "",
                    "",
                    "",
                    "",
                    CliQueryScopeText.noMatchesLabel("balances")))
            : snapshot.balances().stream()
                .map(
                    balance ->
                        List.of(
                            "row",
                            snapshot.account().accountCode().value(),
                            snapshot.account().accountName().value(),
                            snapshot.account().accountType().wireValue(),
                            snapshot.account().accountRole().wireValue(),
                            snapshot.account().normalBalance().wireValue(),
                            snapshot.effectiveDateFrom().map(LocalDate::toString).orElse(""),
                            snapshot.effectiveDateTo().map(LocalDate::toString).orElse(""),
                            balance.netAmount().currencyUnit().code(),
                            CliQueryScopeText.displayMoney(balance.debitTotal()),
                            CliQueryScopeText.displayMoney(balance.creditTotal()),
                            CliQueryScopeText.displayMoney(balance.netAmount()),
                            balance.balanceSide().wireValue(),
                            ""))
                .toList());
  }
}
