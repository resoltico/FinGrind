package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import java.util.List;

/** Renders declared-account page payloads for human and CSV output modes. */
final class CliAccountPageOutputRenderer {
  private CliAccountPageOutputRenderer() {}

  static String renderHuman(AccountPage page) {
    String header =
        CliTextFormat.renderKeyValueBlock(CliBookIdentityDisplay.summaryRows(page.bookIdentity()));
    String nextCursor =
        page.nextCursor().isPresent() ? page.nextCursor().orElseThrow().wireValue() : "(none)";
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Returned accounts", Integer.toString(page.accounts().size())),
                List.of("Limit", Integer.toString(page.limit())),
                List.of("Next cursor", nextCursor)));
    String accounts =
        page.accounts().isEmpty()
            ? "(none)"
            : page.accounts().stream()
                .map(
                    account ->
                        CliTextFormat.renderSummaryBlock(
                            account.accountCode().value() + " | " + account.accountName().value(),
                            CliTextFormat.renderKeyValueBlock(
                                List.of(
                                    List.of(
                                        "Parent account",
                                        account
                                            .accountTaxonomy()
                                            .parentAccountCode()
                                            .map(parent -> parent.value())
                                            .orElse("(none)")),
                                    List.of(
                                        "Account type",
                                        CliQueryOutputFormatter.displayLineTypeLabel(
                                            account.accountType())),
                                    List.of(
                                        "Account role",
                                        CliQueryOutputFormatter.displayAccountRoleLabel(
                                            account.accountRole())),
                                    List.of(
                                        "Financial-position line",
                                        account
                                            .accountTaxonomy()
                                            .financialPositionLineClassification()
                                            .map(
                                                CliQueryOutputFormatter
                                                    ::displayFinancialPositionLineClassification)
                                            .orElse("(none)")),
                                    List.of(
                                        "Profit-and-loss line",
                                        account
                                            .accountTaxonomy()
                                            .profitAndLossLineClassification()
                                            .map(
                                                CliQueryOutputFormatter
                                                    ::displayProfitAndLossLineClassification)
                                            .orElse("(none)")),
                                    List.of(
                                        "Normal balance",
                                        CliQueryOutputFormatter.displayNormalBalanceLabel(
                                            account.normalBalance())),
                                    List.of(
                                        "Active",
                                        CliQueryOutputFormatter.displayBooleanLabel(
                                            account.active())),
                                    List.of(
                                        "Declared at",
                                        CliHumanDisplay.instant(account.declaredAt()))))))
                .collect(
                    java.util.stream.Collectors.joining(
                        System.lineSeparator() + System.lineSeparator()));
    return CliTextFormat.renderTitledBlock(
        "Accounts",
        header
            + System.lineSeparator()
            + System.lineSeparator()
            + summary
            + System.lineSeparator()
            + System.lineSeparator()
            + accounts);
  }

  static String renderCsv(AccountPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "accountCode",
            "accountName",
            "parentAccountCode",
            "accountType",
            "accountRole",
            "financialPositionLineClassification",
            "profitAndLossLineClassification",
            "normalBalance",
            "active",
            "declaredAt"),
        page.accounts().stream()
            .map(
                account ->
                    List.of(
                        account.accountCode().value(),
                        account.accountName().value(),
                        account
                            .accountTaxonomy()
                            .parentAccountCode()
                            .map(parent -> parent.value())
                            .orElse(""),
                        account.accountType().wireValue(),
                        account.accountRole().wireValue(),
                        account
                            .accountTaxonomy()
                            .financialPositionLineClassification()
                            .map(classification -> classification.wireValue())
                            .orElse(""),
                        account
                            .accountTaxonomy()
                            .profitAndLossLineClassification()
                            .map(classification -> classification.wireValue())
                            .orElse(""),
                        account.normalBalance().wireValue(),
                        Boolean.toString(account.active()),
                        account.declaredAt().toString()))
            .toList());
  }
}
