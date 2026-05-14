package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import java.time.LocalDate;
import java.util.List;

/** Renders account-balance query payloads for human and CSV output modes. */
final class CliAccountBalanceOutputRenderer {
  private CliAccountBalanceOutputRenderer() {}

  static String renderHuman(AccountBalanceSnapshot snapshot) {
    String header =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Entity", snapshot.bookIdentity().entityName().value()),
                List.of("Functional currency", snapshot.bookIdentity().functionalCurrency().code()),
                List.of("Fiscal year start", snapshot.bookIdentity().fiscalYearStart().wireValue()),
                List.of(
                    "Posting coverage",
                    CliQueryOutputFormatter.displayPostingCoverage(snapshot.postingCoverage())),
                List.of("Account", snapshot.account().accountCode().value()),
                List.of("Name", snapshot.account().accountName().value()),
                List.of(
                    "Account type",
                    CliQueryOutputFormatter.displayLineTypeLabel(snapshot.account().accountType())),
                List.of(
                    "Account role",
                    CliQueryOutputFormatter.displayAccountRoleLabel(
                        snapshot.account().accountRole())),
                List.of(
                    "Normal balance",
                    CliQueryOutputFormatter.displayNormalBalanceLabel(
                        snapshot.account().normalBalance())),
                List.of(
                    "Active",
                    CliQueryOutputFormatter.displayBooleanLabel(snapshot.account().active())),
                List.of(
                    "Range",
                    CliQueryOutputFormatter.dateRange(
                        snapshot.effectiveDateFrom().orElse(null),
                        snapshot.effectiveDateTo().orElse(null)))));
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
            "entityName",
            "functionalCurrency",
            "fiscalYearStart",
            "postingCoverage",
            "accountCode",
            "accountName",
            "accountType",
            "accountRole",
            "normalBalance",
            "effectiveDateFrom",
            "effectiveDateFromMeaning",
            "effectiveDateTo",
            "effectiveDateToMeaning",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide"),
        snapshot.balances().stream()
            .map(
                balance ->
                    List.of(
                        snapshot.bookIdentity().entityName().value(),
                        snapshot.bookIdentity().functionalCurrency().code(),
                        snapshot.bookIdentity().fiscalYearStart().wireValue(),
                        snapshot.postingCoverage().wireValue(),
                        snapshot.account().accountCode().value(),
                        snapshot.account().accountName().value(),
                        snapshot.account().accountType().wireValue(),
                        snapshot.account().accountRole().wireValue(),
                        snapshot.account().normalBalance().wireValue(),
                        snapshot.effectiveDateFrom().map(LocalDate::toString).orElse(""),
                        CliQueryOutputFormatter.lowerDateBoundaryMeaning(
                            snapshot.effectiveDateFrom().orElse(null)),
                        snapshot.effectiveDateTo().map(LocalDate::toString).orElse(""),
                        CliQueryOutputFormatter.upperDateBoundaryMeaning(
                            snapshot.effectiveDateTo().orElse(null)),
                        balance.netAmount().currencyUnit().code(),
                        CliQueryOutputFormatter.displayMoney(balance.debitTotal()),
                        CliQueryOutputFormatter.displayMoney(balance.creditTotal()),
                        CliQueryOutputFormatter.displayMoney(balance.netAmount()),
                        balance.balanceSide().wireValue()))
            .toList());
  }
}
