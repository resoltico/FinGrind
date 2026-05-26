package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import java.util.List;

/** Renders declared-account page payloads for text and CSV output modes. */
final class CliAccountPageOutputRenderer {
  private CliAccountPageOutputRenderer() {}

  static String renderText(AccountPage page) {
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
            ? CliQueryOutputFormatter.noMatchesLabel("accounts")
            : CliTextFormat.renderTable(
                List.of("Account", "Name", "Type", "Statement line", "Parent", "Normal", "Active"),
                page.accounts().stream()
                    .map(
                        account ->
                            List.of(
                                account.accountCode().value(),
                                account.accountName().value(),
                                CliQueryOutputFormatter.displayLineTypeLabel(account.accountType()),
                                account
                                    .accountTaxonomy()
                                    .financialPositionLineClassification()
                                    .map(
                                        CliQueryOutputFormatter
                                            ::displayFinancialPositionLineClassification)
                                    .orElseGet(
                                        () ->
                                            account
                                                .accountTaxonomy()
                                                .profitAndLossLineClassification()
                                                .map(
                                                    CliQueryOutputFormatter
                                                        ::displayProfitAndLossLineClassification)
                                                .orElse("(none)")),
                                account
                                    .accountTaxonomy()
                                    .parentAccountCode()
                                    .map(parent -> parent.value())
                                    .orElse("(none)"),
                                CliQueryOutputFormatter.displayNormalBalanceLabel(
                                    account.normalBalance()),
                                CliQueryOutputFormatter.displayBooleanLabel(account.active())))
                    .toList());
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
