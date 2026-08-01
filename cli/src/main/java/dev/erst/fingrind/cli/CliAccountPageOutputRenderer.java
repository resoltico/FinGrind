package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import java.util.List;

/** Renders declared-account page payloads for text and CSV output modes. */
final class CliAccountPageOutputRenderer {
  private static final String RECORD_KIND = CliCsvExportFamilies.ACCOUNTS;

  private CliAccountPageOutputRenderer() {}

  static String renderText(AccountPage page, boolean withContext) {
    String accounts =
        page.accounts().isEmpty()
            ? ""
            : CliTextFormat.renderTable(
                List.of(
                    "Account",
                    "Name",
                    "Type",
                    "Unit",
                    "Financial position line",
                    "Cash-flow asset",
                    "Profit or loss line",
                    "Parent",
                    "Contra of",
                    "Normal",
                    "Active"),
                page.accounts().stream()
                    .map(
                        account ->
                            List.of(
                                account.accountCode().value(),
                                account.accountName().value(),
                                CliAccountStatementLabels.displayLineTypeLabel(
                                    account.accountType()),
                                displayUnitOfMeasure(account),
                                account
                                    .accountTaxonomy()
                                    .financialPositionLineClassification()
                                    .map(
                                        CliAccountStatementLabels
                                            ::displayFinancialPositionLineClassification)
                                    .orElse("(none)"),
                                account
                                    .accountTaxonomy()
                                    .cashFlowAssetClassification()
                                    .map(
                                        CliAccountStatementLabels
                                            ::displayCashFlowAssetClassification)
                                    .orElse("(none)"),
                                account
                                    .accountTaxonomy()
                                    .profitAndLossLineClassification()
                                    .map(
                                        CliAccountStatementLabels
                                            ::displayProfitAndLossLineClassification)
                                    .orElse("(none)"),
                                account
                                    .accountTaxonomy()
                                    .parentAccountCode()
                                    .map(parent -> parent.value())
                                    .orElse("(none)"),
                                account
                                    .accountTaxonomy()
                                    .contraOfAccountCode()
                                    .map(parent -> parent.value())
                                    .orElse("(none)"),
                                CliAccountStatementLabels.displayNormalBalanceLabel(
                                    account.normalBalance()),
                                CliQueryScopeText.displayBooleanLabel(account.active())))
                    .toList());
    return CliReportRenderSupport.renderPagedListText(
        new CliPagedListText(
            "Accounts",
            "accounts",
            "accounts",
            page.accounts().size(),
            page.limit(),
            page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)"),
            accounts,
            withContext,
            CliBookIdentityDisplay.contextRows(page.bookIdentity())));
  }

  static String renderCsv(AccountPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "recordKind",
            "accountCode",
            "accountName",
            "parentAccountCode",
            "contraOfAccountCode",
            "accountType",
            "unitOfMeasureToken",
            "quantityScale",
            "financialPositionLineClassification",
            "cashFlowAssetClassification",
            "profitAndLossLineClassification",
            "normalBalance",
            "active",
            "declaredAt",
            "message"),
        page.accounts().isEmpty()
            ? List.of(
                List.of(
                    CliCsvExportFamilies.ACCOUNTS,
                    "accounts:scope-empty",
                    "",
                    "scope-empty",
                    RECORD_KIND,
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
                    "",
                    "",
                    "",
                    CliQueryScopeText.noMatchesLabel("accounts")))
            : page.accounts().stream()
                .map(
                    account ->
                        List.of(
                            CliCsvExportFamilies.ACCOUNTS,
                            "account:" + account.accountCode().value(),
                            "",
                            "account",
                            RECORD_KIND,
                            account.accountCode().value(),
                            account.accountName().value(),
                            account
                                .accountTaxonomy()
                                .parentAccountCode()
                                .map(parent -> parent.value())
                                .orElse(""),
                            account
                                .accountTaxonomy()
                                .contraOfAccountCode()
                                .map(parent -> parent.value())
                                .orElse(""),
                            account.accountType().wireValue(),
                            account.unitOfMeasure() == null ? "" : account.unitOfMeasure().token(),
                            account.unitOfMeasure() == null
                                ? ""
                                : Integer.toString(account.unitOfMeasure().quantityScale()),
                            account
                                .accountTaxonomy()
                                .financialPositionLineClassification()
                                .map(classification -> classification.wireValue())
                                .orElse(""),
                            account
                                .accountTaxonomy()
                                .cashFlowAssetClassification()
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

  private static String displayUnitOfMeasure(
      dev.erst.fingrind.contract.bookkeeping.DeclaredAccount account) {
    return account.unitOfMeasure() == null
        ? "(none)"
        : account.unitOfMeasure().token()
            + " (scale "
            + account.unitOfMeasure().quantityScale()
            + ")";
  }
}
