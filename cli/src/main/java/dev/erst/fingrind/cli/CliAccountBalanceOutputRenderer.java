package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.protocol.OperationId;
import java.time.LocalDate;
import java.util.List;

/** Renders account-balance query payloads for text and CSV output modes. */
final class CliAccountBalanceOutputRenderer {
  private static final String OPERATION_ID = OperationId.ACCOUNT_BALANCE.wireName();
  private static final String RECORD_KIND = CliCsvExportFamilies.ACCOUNT_BALANCE;

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

  static String renderCsv(AccountBalanceSnapshot snapshot) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "recordKind",
            "accountCode",
            "accountName",
            "accountType",
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
                    CliCsvExportFamilies.ACCOUNT_BALANCE,
                    OPERATION_ID + "-empty:" + snapshot.account().accountCode().value(),
                    "",
                    "scope-empty",
                    RECORD_KIND,
                    snapshot.account().accountCode().value(),
                    snapshot.account().accountName().value(),
                    snapshot.account().accountType().wireValue(),
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
                            CliCsvExportFamilies.ACCOUNT_BALANCE,
                            OPERATION_ID
                                + ":"
                                + snapshot.account().accountCode().value()
                                + ":"
                                + balance.netAmount().currencyUnit().code(),
                            "",
                            "balance",
                            RECORD_KIND,
                            snapshot.account().accountCode().value(),
                            snapshot.account().accountName().value(),
                            snapshot.account().accountType().wireValue(),
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

  private static List<List<String>> mergeContextRows(
      List<List<String>> firstRows, List<List<String>> secondRows) {
    List<List<String>> rows = new java.util.ArrayList<>(firstRows);
    rows.addAll(secondRows);
    return List.copyOf(rows);
  }
}
