package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.util.ArrayList;
import java.util.List;

/** Assembles complete account-ledger CSV rows from reusable column groups. */
final class CliAccountLedgerCsvRowFactory {
  private static final String RECORD_KIND = CliCsvExportFamilies.ACCOUNT_LEDGER;

  private CliAccountLedgerCsvRowFactory() {}

  static List<String> summaryRow(
      AccountLedgerReport report,
      String currencyCode,
      CurrencyBalance opening,
      CurrencyBalance closing) {
    return ledgerCsvRow(
        CliCsvExportFamilies.ACCOUNT_LEDGER,
        "ledger-summary:" + report.account().accountCode().value() + ":" + currencyCode,
        "",
        "ledger-summary",
        RECORD_KIND,
        new LedgerCsvRowParts(
            CliAccountLedgerCsvColumns.reportColumns(report, currencyCode),
            CliAccountLedgerCsvColumns.summaryBalanceColumns(opening, closing),
            CliAccountLedgerCsvColumns.blankEntryColumns(),
            CliAccountLedgerCsvColumns.blankMovementColumns(),
            CliAccountLedgerCsvColumns.blankEvidenceColumns()),
        "");
  }

  static List<String> entryRow(AccountLedgerReport report, AccountLedgerEntry entry) {
    return entryRelatedRow(
        new LedgerCsvRowIdentity(
            "ledger-entry:" + entry.postingFact().postingId().value(),
            "",
            "entry",
            RECORD_KIND,
            ""),
        report,
        entry,
        entry.movement().netAmount().currencyUnit().code(),
        CliAccountLedgerCsvColumns.movementColumns(
            CliQueryScopeText.displayMoney(entry.movement().debitTotal()),
            CliQueryScopeText.displayMoney(entry.movement().creditTotal()),
            CliQueryScopeText.displayMoney(entry.runningNetAmount()),
            entry.runningBalanceSide().wireValue()),
        CliAccountLedgerCsvColumns.blankEvidenceColumns());
  }

  static List<String> counterpartRow(
      AccountLedgerReport report, AccountLedgerEntry entry, String counterpartAccountCode) {
    return entryRelatedRow(
        new LedgerCsvRowIdentity(
            "ledger-counterpart:"
                + entry.postingFact().postingId().value()
                + ":"
                + counterpartAccountCode,
            "ledger-entry:" + entry.postingFact().postingId().value(),
            "counterpart-account",
            RECORD_KIND,
            ""),
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns(counterpartAccountCode, "", "", "", ""));
  }

  static List<String> sourceDocumentRow(
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String sourceDocumentId,
      String sourceDocumentType) {
    return entryRelatedRow(
        new LedgerCsvRowIdentity(
            "ledger-source-document:"
                + entry.postingFact().postingId().value()
                + ":"
                + sourceDocumentId,
            "ledger-entry:" + entry.postingFact().postingId().value(),
            "source-document",
            RECORD_KIND,
            ""),
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns(
            "", sourceDocumentId, sourceDocumentType, "", ""));
  }

  static List<String> approvalRow(
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String approvalId,
      String approvalDecision) {
    return entryRelatedRow(
        new LedgerCsvRowIdentity(
            "ledger-approval:" + entry.postingFact().postingId().value() + ":" + approvalId,
            "ledger-entry:" + entry.postingFact().postingId().value(),
            "approval",
            RECORD_KIND,
            ""),
        report,
        entry,
        "",
        CliAccountLedgerCsvColumns.blankMovementColumns(),
        CliAccountLedgerCsvColumns.evidenceColumns("", "", "", approvalId, approvalDecision));
  }

  static List<String> emptyRow(AccountLedgerReport report) {
    return ledgerCsvRow(
        CliCsvExportFamilies.ACCOUNT_LEDGER,
        "ledger-scope-empty:" + report.account().accountCode().value(),
        "",
        "scope-empty",
        RECORD_KIND,
        new LedgerCsvRowParts(
            CliAccountLedgerCsvColumns.reportColumns(
                report, report.bookIdentity().functionalCurrency().code()),
            CliAccountLedgerCsvColumns.blankSummaryBalanceColumns(),
            CliAccountLedgerCsvColumns.blankEntryColumns(),
            CliAccountLedgerCsvColumns.blankMovementColumns(),
            CliAccountLedgerCsvColumns.blankEvidenceColumns()),
        CliQueryScopeText.noMatchesLabel("ledger entries"));
  }

  private static List<String> entryRelatedRow(
      LedgerCsvRowIdentity rowIdentity,
      AccountLedgerReport report,
      AccountLedgerEntry entry,
      String currencyCode,
      List<String> movementColumns,
      List<String> evidenceColumns) {
    return ledgerCsvRow(
        CliCsvExportFamilies.ACCOUNT_LEDGER,
        rowIdentity.rowId(),
        rowIdentity.parentRowId(),
        rowIdentity.relationKind(),
        rowIdentity.rowKind(),
        new LedgerCsvRowParts(
            CliAccountLedgerCsvColumns.reportColumns(report, currencyCode),
            CliAccountLedgerCsvColumns.blankSummaryBalanceColumns(),
            CliAccountLedgerCsvColumns.entryColumns(entry),
            movementColumns,
            evidenceColumns),
        rowIdentity.message());
  }

  private static List<String> ledgerCsvRow(
      String exportFamily,
      String rowId,
      String parentRowId,
      String relationKind,
      String rowKind,
      LedgerCsvRowParts rowParts,
      String message) {
    List<String> row = new ArrayList<>(39);
    row.add(exportFamily);
    row.add(rowId);
    row.add(parentRowId);
    row.add(relationKind);
    row.add(rowKind);
    row.addAll(rowParts.reportColumns());
    row.addAll(rowParts.summaryBalanceColumns());
    row.addAll(rowParts.entryColumns());
    row.addAll(rowParts.movementColumns());
    row.addAll(rowParts.evidenceColumns());
    row.add(message);
    return List.copyOf(row);
  }

  private record LedgerCsvRowParts(
      List<String> reportColumns,
      List<String> summaryBalanceColumns,
      List<String> entryColumns,
      List<String> movementColumns,
      List<String> evidenceColumns) {}

  private record LedgerCsvRowIdentity(
      String rowId, String parentRowId, String relationKind, String rowKind, String message) {}
}
