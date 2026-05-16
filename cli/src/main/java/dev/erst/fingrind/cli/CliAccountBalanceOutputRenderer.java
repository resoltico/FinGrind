package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import java.time.LocalDate;
import java.util.List;

/** Renders account-balance query payloads for human and CSV output modes. */
final class CliAccountBalanceOutputRenderer {
  private CliAccountBalanceOutputRenderer() {}

  static String renderHuman(AccountBalanceSnapshot snapshot) {
    List<List<String>> headerRows =
        new java.util.ArrayList<>(CliBookIdentityDisplay.rows(snapshot.bookIdentity()));
    headerRows.add(
        List.of(
            "Posting coverage",
            CliQueryOutputFormatter.displayPostingCoverage(snapshot.postingCoverage())));
    headerRows.add(List.of("Account", snapshot.account().accountCode().value()));
    headerRows.add(List.of("Name", snapshot.account().accountName().value()));
    headerRows.add(
        List.of(
            "Account type",
            CliQueryOutputFormatter.displayLineTypeLabel(snapshot.account().accountType())));
    headerRows.add(
        List.of(
            "Account role",
            CliQueryOutputFormatter.displayAccountRoleLabel(snapshot.account().accountRole())));
    headerRows.add(
        List.of(
            "Normal balance",
            CliQueryOutputFormatter.displayNormalBalanceLabel(snapshot.account().normalBalance())));
    headerRows.add(
        List.of(
            "Active", CliQueryOutputFormatter.displayBooleanLabel(snapshot.account().active())));
    headerRows.add(
        List.of(
            "Range",
            CliQueryOutputFormatter.dateRange(
                snapshot.effectiveDateFrom().orElse(null),
                snapshot.effectiveDateTo().orElse(null))));
    String header = CliTextFormat.renderKeyValueBlock(List.copyOf(headerRows));
    String balances =
        CliTextFormat.renderTable(
            List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
            snapshot.balances().stream().map(CliQueryOutputFormatter::balanceHumanRow).toList(),
            1,
            2,
            3);
    return CliTextFormat.renderTitledBlock(
        "Account Balance", header + System.lineSeparator() + System.lineSeparator() + balances);
  }

  static String renderCsv(AccountBalanceSnapshot snapshot) {
    return CliTextFormat.renderCsv(
        List.of(
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
            "balanceSide"),
        snapshot.balances().stream()
            .map(
                balance ->
                    List.of(
                        snapshot.account().accountCode().value(),
                        snapshot.account().accountName().value(),
                        snapshot.account().accountType().wireValue(),
                        snapshot.account().accountRole().wireValue(),
                        snapshot.account().normalBalance().wireValue(),
                        snapshot.effectiveDateFrom().map(LocalDate::toString).orElse(""),
                        snapshot.effectiveDateTo().map(LocalDate::toString).orElse(""),
                        balance.netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(balance.debitTotal()),
                        CliQueryOutputFormatter.displayMoney(balance.creditTotal()),
                        CliQueryOutputFormatter.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }
}
