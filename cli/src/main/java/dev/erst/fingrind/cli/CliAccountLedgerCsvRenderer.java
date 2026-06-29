package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import java.util.ArrayList;
import java.util.List;

/** Renders the account-ledger CSV contract. */
final class CliAccountLedgerCsvRenderer {
  private static final List<String> CSV_HEADERS =
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
          "active",
          "effectiveDateFrom",
          "effectiveDateTo",
          "currencyCode",
          "openingDebitTotal",
          "openingCreditTotal",
          "openingNetAmount",
          "openingBalanceSide",
          "closingDebitTotal",
          "closingCreditTotal",
          "closingNetAmount",
          "closingBalanceSide",
          "effectiveDate",
          "recordedAt",
          "postingId",
          "postingKind",
          "postingOriginKind",
          "reversalState",
          "reversalTarget",
          "debitAmount",
          "creditAmount",
          "runningNetAmount",
          "runningBalanceSide",
          "counterpartAccountCode",
          "sourceDocumentId",
          "sourceDocumentType",
          "approvalId",
          "approvalDecision",
          "message");

  private CliAccountLedgerCsvRenderer() {}

  static String render(AccountLedgerReport report) {
    List<String> currencyCodes = currencyCodes(report);
    return CliTextFormat.renderCsv(
        CSV_HEADERS,
        java.util.stream.Stream.concat(
                currencyCodes.stream().map(currencyCode -> summaryRow(report, currencyCode)),
                report.entries().isEmpty()
                    ? java.util.stream.Stream.of(CliAccountLedgerCsvRowFactory.emptyRow(report))
                    : report.entries().stream().flatMap(entry -> entryRows(report, entry)))
            .toList());
  }

  private static List<String> summaryRow(AccountLedgerReport report, String currencyCode) {
    CurrencyBalance opening =
        CliReportRenderSupport.balanceForCurrency(report.openingBalances(), currencyCode);
    CurrencyBalance closing =
        CliReportRenderSupport.balanceForCurrency(report.closingBalances(), currencyCode);
    return CliAccountLedgerCsvRowFactory.summaryRow(report, currencyCode, opening, closing);
  }

  private static java.util.stream.Stream<List<String>> entryRows(
      AccountLedgerReport report, AccountLedgerEntry entry) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(CliAccountLedgerCsvRowFactory.entryRow(report, entry));
    entry.postingFact().journalEntry().lines().stream()
        .map(JournalLine::accountCode)
        .map(AccountCode::value)
        .filter(accountCode -> !accountCode.equals(report.account().accountCode().value()))
        .distinct()
        .map(
            accountCode -> CliAccountLedgerCsvRowFactory.counterpartRow(report, entry, accountCode))
        .forEach(rows::add);
    entry.postingFact().evidence().sourceDocuments().stream()
        .map(
            sourceDocument ->
                CliAccountLedgerCsvRowFactory.sourceDocumentRow(
                    report,
                    entry,
                    sourceDocument.sourceDocumentId().value(),
                    sourceDocument.sourceDocumentType().value()))
        .forEach(rows::add);
    entry.postingFact().evidence().approvals().stream()
        .map(
            approval ->
                CliAccountLedgerCsvRowFactory.approvalRow(
                    report, entry, approval.approvalId().value(), approval.decision().wireValue()))
        .forEach(rows::add);
    return rows.stream();
  }

  private static List<String> currencyCodes(AccountLedgerReport report) {
    List<String> currencyCodes =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(
                    report.openingBalances().stream()
                        .map(balance -> balance.netAmount().currencyUnit().code()),
                    report.entries().stream()
                        .map(entry -> entry.movement().netAmount().currencyUnit().code())),
                report.closingBalances().stream()
                    .map(balance -> balance.netAmount().currencyUnit().code()))
            .distinct()
            .toList();
    return currencyCodes.isEmpty()
        ? List.of(report.bookIdentity().functionalCurrency().code())
        : currencyCodes;
  }
}
