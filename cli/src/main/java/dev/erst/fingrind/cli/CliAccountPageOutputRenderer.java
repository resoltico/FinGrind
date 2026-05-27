package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import java.util.List;

/** Renders declared-account page payloads for text and CSV output modes. */
final class CliAccountPageOutputRenderer {
  private CliAccountPageOutputRenderer() {}

  static String renderText(AccountPage page) {
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
            ? CliQueryScopeText.noMatchesLabel("accounts")
            : CliTextFormat.renderTable(
                List.of("Account", "Name", "Type", "Statement line", "Parent", "Normal", "Active"),
                page.accounts().stream()
                    .map(
                        account ->
                            List.of(
                                account.accountCode().value(),
                                account.accountName().value(),
                                CliAccountStatementLabels.displayLineTypeLabel(
                                    account.accountType()),
                                account
                                    .accountTaxonomy()
                                    .financialPositionLineClassification()
                                    .map(
                                        CliAccountStatementLabels
                                            ::displayFinancialPositionLineClassification)
                                    .orElseGet(
                                        () ->
                                            account
                                                .accountTaxonomy()
                                                .profitAndLossLineClassification()
                                                .map(
                                                    CliAccountStatementLabels
                                                        ::displayProfitAndLossLineClassification)
                                                .orElse("(none)")),
                                account
                                    .accountTaxonomy()
                                    .parentAccountCode()
                                    .map(parent -> parent.value())
                                    .orElse("(none)"),
                                CliAccountStatementLabels.displayNormalBalanceLabel(
                                    account.normalBalance()),
                                CliQueryScopeText.displayBooleanLabel(account.active())))
                    .toList());
    return CliTextFormat.renderTitledBlock(
        "Accounts",
        CliReportRenderSupport.joinSections(
            summary,
            accounts,
            CliReportRenderSupport.keyValueSection(
                "Context", CliBookIdentityDisplay.summaryRows(page.bookIdentity()))));
  }

  static String renderCsv(AccountPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "recordKind",
            "accountCode",
            "accountName",
            "parentAccountCode",
            "accountType",
            "accountRole",
            "financialPositionLineClassification",
            "profitAndLossLineClassification",
            "normalBalance",
            "active",
            "declaredAt",
            "message"),
        page.accounts().isEmpty()
            ? List.of(
                List.of(
                    "empty",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    CliQueryScopeText.noMatchesLabel("accounts")))
            : page.accounts().stream()
                .map(
                    account ->
                        List.of(
                            "row",
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
                            account.declaredAt().toString(),
                            ""))
                .toList());
  }
}
