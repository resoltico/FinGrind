package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;

/** Maps report-domain payloads into CLI JSON report models. */
final class CliReportPayloadMapper {
  private CliReportPayloadMapper() {}

  static CliReportJsonModels.TrialBalancePayload trialBalancePayload(TrialBalanceReport report) {
    return new CliReportJsonModels.TrialBalancePayload(
        report.effectiveDateTo().map(Object::toString).orElse(null),
        report.rows().stream().map(CliReportPayloadMapper::trialBalanceRowPayload).toList());
  }

  static CliReportJsonModels.AccountLedgerPayload accountLedgerPayload(AccountLedgerReport report) {
    return new CliReportJsonModels.AccountLedgerPayload(
        report.account().accountCode().value(),
        report.account().accountName().value(),
        report.account().normalBalance().wireValue(),
        report.account().active(),
        report.account().declaredAt().toString(),
        report.effectiveDateRange().effectiveDateFrom().map(Object::toString).orElse(null),
        report.effectiveDateRange().effectiveDateTo().map(Object::toString).orElse(null),
        report.openingBalances().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.entries().stream()
            .map(entry -> accountLedgerEntryPayload(report.account(), entry))
            .toList(),
        report.closingBalances().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  static CliReportJsonModels.PeriodSummaryPayload periodSummaryPayload(PeriodSummaryReport report) {
    return new CliReportJsonModels.PeriodSummaryPayload(
        report.effectiveDateFrom().toString(),
        report.effectiveDateTo().toString(),
        report.postingCount(),
        report.postingLineCount(),
        report.accountsTouched(),
        report.currencyTotals().stream()
            .map(summary -> CliPayloadAssembler.balancePayload(summary.totals()))
            .toList(),
        report.accountActivity().stream()
            .map(CliReportPayloadMapper::periodAccountActivityPayload)
            .toList());
  }

  private static CliReportJsonModels.TrialBalanceRowPayload trialBalanceRowPayload(
      TrialBalanceRow row) {
    return new CliReportJsonModels.TrialBalanceRowPayload(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().normalBalance().wireValue(),
        row.account().active(),
        row.account().declaredAt().toString(),
        row.balance().netAmount().currencyCode().value(),
        row.balance().debitTotal().amount().toPlainString(),
        row.balance().creditTotal().amount().toPlainString(),
        row.balance().netAmount().amount().toPlainString(),
        row.balance().balanceSide().wireValue());
  }

  private static CliReportJsonModels.AccountLedgerEntryPayload accountLedgerEntryPayload(
      DeclaredAccount account, AccountLedgerEntry entry) {
    return new CliReportJsonModels.AccountLedgerEntryPayload(
        entry.postingFact().postingId().value(),
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.movement().netAmount().currencyCode().value(),
        entry.movement().debitTotal().amount().toPlainString(),
        entry.movement().creditTotal().amount().toPlainString(),
        entry.runningNetAmount().amount().toPlainString(),
        entry.runningBalanceSide().wireValue(),
        CliBookPayloadMapper.counterpartAccounts(account, entry.postingFact()));
  }

  private static CliReportJsonModels.PeriodAccountActivityPayload periodAccountActivityPayload(
      PeriodAccountActivityRow row) {
    return new CliReportJsonModels.PeriodAccountActivityPayload(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().normalBalance().wireValue(),
        row.account().active(),
        row.account().declaredAt().toString(),
        row.movement().netAmount().currencyCode().value(),
        row.movement().debitTotal().amount().toPlainString(),
        row.movement().creditTotal().amount().toPlainString(),
        row.movement().netAmount().amount().toPlainString(),
        row.movement().balanceSide().wireValue());
  }
}
