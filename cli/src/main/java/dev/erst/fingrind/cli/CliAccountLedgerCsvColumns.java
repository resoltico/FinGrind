package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;

/** Builds reusable account-ledger CSV column groups. */
final class CliAccountLedgerCsvColumns {
  private CliAccountLedgerCsvColumns() {}

  static List<String> reportColumns(AccountLedgerReport report, String currencyCode) {
    return List.of(
        report.account().accountCode().value(),
        report.account().accountName().value(),
        report.account().accountType().wireValue(),
        report.account().accountRole().wireValue(),
        report.account().normalBalance().wireValue(),
        Boolean.toString(report.account().active()),
        report.effectiveDateRange().effectiveDateFrom().map(LocalDate::toString).orElse(""),
        report.effectiveDateRange().effectiveDateTo().map(LocalDate::toString).orElse(""),
        currencyCode);
  }

  static List<String> summaryBalanceColumns(CurrencyBalance opening, CurrencyBalance closing) {
    return List.of(
        CliQueryScopeText.displayMoney(opening.debitTotal()),
        CliQueryScopeText.displayMoney(opening.creditTotal()),
        CliQueryScopeText.displayMoney(opening.netAmount()),
        opening.balanceSide().wireValue(),
        CliQueryScopeText.displayMoney(closing.debitTotal()),
        CliQueryScopeText.displayMoney(closing.creditTotal()),
        CliQueryScopeText.displayMoney(closing.netAmount()),
        closing.balanceSide().wireValue());
  }

  static List<String> blankSummaryBalanceColumns() {
    return List.of("", "", "", "", "", "", "", "");
  }

  static List<String> entryColumns(AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.postingFact().postingId().value(),
        entry.postingFact().postingKind().wireValue(),
        entry.postingFact().postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(entry.postingFact()),
        CliPostingLabels.reversalTargetCsv(entry.postingFact()));
  }

  static List<String> blankEntryColumns() {
    return List.of("", "", "", "", "", "", "");
  }

  static List<String> movementColumns(
      String debitAmount, String creditAmount, String runningNetAmount, String runningBalanceSide) {
    return List.of(debitAmount, creditAmount, runningNetAmount, runningBalanceSide);
  }

  static List<String> blankMovementColumns() {
    return movementColumns("", "", "", "");
  }

  static List<String> evidenceColumns(
      String counterpartAccountCode,
      String sourceDocumentId,
      String sourceDocumentType,
      String approvalId,
      String approvalDecision) {
    return List.of(
        counterpartAccountCode, sourceDocumentId, sourceDocumentType, approvalId, approvalDecision);
  }

  static List<String> blankEvidenceColumns() {
    return evidenceColumns("", "", "", "", "");
  }
}
