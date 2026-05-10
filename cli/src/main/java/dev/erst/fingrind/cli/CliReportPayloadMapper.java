package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.MonetaryAmount;
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
        MonetaryAmount.of(row.balance().debitTotal()),
        MonetaryAmount.of(row.balance().creditTotal()),
        MonetaryAmount.of(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  private static CliReportJsonModels.AccountLedgerEntryPayload accountLedgerEntryPayload(
      DeclaredAccount account, AccountLedgerEntry entry) {
    return new CliReportJsonModels.AccountLedgerEntryPayload(
        entry.postingFact().postingId().value(),
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        MonetaryAmount.of(entry.movement().debitTotal()),
        MonetaryAmount.of(entry.movement().creditTotal()),
        MonetaryAmount.of(entry.runningNetAmount()),
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
        MonetaryAmount.of(row.movement().debitTotal()),
        MonetaryAmount.of(row.movement().creditTotal()),
        MonetaryAmount.of(row.movement().netAmount()),
        row.movement().balanceSide().wireValue());
  }
}
