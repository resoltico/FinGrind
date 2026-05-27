package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.ArrayList;
import java.util.List;

/** Assembles complete account-ledger CSV rows from reusable column groups. */
final class CliAccountLedgerCsvRowFactory {
  private CliAccountLedgerCsvRowFactory() {}

  static List<String> summaryRow(
      AccountLedgerReport report,
      String currencyCode,
      CurrencyBalance opening,
      CurrencyBalance closing) {
    return ledgerCsvRow(
        "summary",
        CliAccountLedgerCsvColumns.reportColumns(report, currencyCode),
        CliAccountLedgerCsvColumns.summaryBalanceColumns(opening, closing),
        CliAccountLedgerCsvColumns.blankEntryColumns(),
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.blankEvidenceColumns(),
        "");
  }

  static List<String> entryRow(AccountLedgerReport report, AccountLedgerEntry entry) {
    return entryRelatedRow(
        "entry",
        report,
        entry,
        entry.movement().netAmount().currencyUnit().code(),
        CliAccountLedgerCsvColumns.movementColumns(
            CliQueryScopeText.displayMoney(entry.movement().debitTotal()),
            CliQueryScopeText.displayMoney(entry.movement().creditTotal()),
            CliQueryScopeText.displayMoney(entry.runningNetAmount()),
            entry.runningBalanceSide().wireValue()),
        CliAccountLedgerCsvColumns.blankEvidenceColumns(),
        "");
  }

  static List<String> counterpartRow(
      AccountLedgerReport report, AccountLedgerEntry entry, String counterpartAccountCode) {
    return entryRelatedRow(
        "counterpart-account",
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns(counterpartAccountCode, "", "", "", ""),
        "");
  }

  static List<String> sourceDocumentRow(
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String sourceDocumentId,
      String sourceDocumentType) {
    return entryRelatedRow(
        "source-document",
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns(
            "", sourceDocumentId, sourceDocumentType, "", ""),
        "");
  }

  static List<String> approvalRow(
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String approvalId,
      String approvalDecision) {
    return entryRelatedRow(
        "approval",
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns("", "", "", approvalId, approvalDecision),
        "");
  }

  static List<String> emptyRow(AccountLedgerReport report) {
    return ledgerCsvRow(
        "empty",
        CliAccountLedgerCsvColumns.reportColumns(
            report, report.bookIdentity().functionalCurrency().code()),
        CliAccountLedgerCsvColumns.blankSummaryBalanceColumns(),
        CliAccountLedgerCsvColumns.blankEntryColumns(),
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.blankEvidenceColumns(),
        CliQueryScopeText.noMatchesLabel("ledger entries"));
  }

  private static List<String> entryRelatedRow(
      String rowKind,
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String currencyCode,
      List<String> movementColumns,
      List<String> evidenceColumns,
      String message) {
    return ledgerCsvRow(
        rowKind,
        CliAccountLedgerCsvColumns.reportColumns(report, currencyCode),
        CliAccountLedgerCsvColumns.blankSummaryBalanceColumns(),
        CliAccountLedgerCsvColumns.entryColumns(entry),
        movementColumns,
        evidenceColumns,
        message);
  }

  private static List<String> ledgerCsvRow(
      String rowKind,
      List<String> reportColumns,
      List<String> summaryBalanceColumns,
      List<String> entryColumns,
      List<String> movementColumns,
      List<String> evidenceColumns,
      String message) {
    List<String> row = new ArrayList<>(35);
    row.add(rowKind);
    row.addAll(reportColumns);
    row.addAll(summaryBalanceColumns);
    row.addAll(entryColumns);
    row.addAll(movementColumns);
    row.addAll(evidenceColumns);
    row.add(message);
    return List.copyOf(row);
  }
}
