package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.cli.json.CliStatementJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;

/** Maps report rows and sections into the CLI JSON report payload families. */
final class CliReportRowPayloadMapper {
  private CliReportRowPayloadMapper() {}

  static CliReportJsonModels.TrialBalanceRowPayload trialBalanceRowPayload(TrialBalanceRow row) {
    return new CliReportJsonModels.TrialBalanceRowPayload(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().accountRole().wireValue(),
        row.account().normalBalance().wireValue(),
        row.account().active(),
        row.account().declaredAt().toString(),
        MonetaryAmount.of(row.balance().debitTotal()),
        MonetaryAmount.of(row.balance().creditTotal()),
        MonetaryAmount.of(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  static CliReportJsonModels.AccountLedgerEntryPayload accountLedgerEntryPayload(
      DeclaredAccount account, AccountLedgerEntry entry) {
    return new CliReportJsonModels.AccountLedgerEntryPayload(
        entry.postingFact().postingId().value(),
        entry.postingFact().postingKind().wireValue(),
        entry.postingFact().reversalReference().isPresent() ? "reversal" : "direct",
        entry
            .postingFact()
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(null),
        entry.postingFact().reversalReason().map(reason -> reason.value()).orElse(null),
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        MonetaryAmount.of(entry.movement().debitTotal()),
        MonetaryAmount.of(entry.movement().creditTotal()),
        MonetaryAmount.of(entry.runningNetAmount()),
        entry.runningBalanceSide().wireValue(),
        CliBookPostingPayloadMapper.evidencePayload(entry.postingFact().evidence()),
        CliBookPostingPayloadMapper.counterpartAccounts(account, entry.postingFact()));
  }

  static CliReportJsonModels.PeriodAccountActivityPayload periodAccountActivityPayload(
      PeriodAccountActivityRow row) {
    return new CliReportJsonModels.PeriodAccountActivityPayload(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().accountRole().wireValue(),
        row.account().normalBalance().wireValue(),
        row.account().active(),
        row.account().declaredAt().toString(),
        MonetaryAmount.of(row.movement().debitTotal()),
        MonetaryAmount.of(row.movement().creditTotal()),
        MonetaryAmount.of(row.movement().netAmount()),
        row.movement().balanceSide().wireValue());
  }

  static CliStatementJsonModels.FinancialPositionSectionPayload financialPositionSectionPayload(
      FinancialPositionSection section) {
    return new CliStatementJsonModels.FinancialPositionSectionPayload(
        section.accountType().wireValue(),
        section.rows().stream()
            .map(CliReportRowPayloadMapper::financialPositionRowPayload)
            .toList(),
        section.totals().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  static CliStatementJsonModels.FinancialPositionRowPayload financialPositionRowPayload(
      FinancialPositionRow row) {
    return new CliStatementJsonModels.FinancialPositionRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().wireValue(),
        row.lineRole().map(dev.erst.fingrind.core.AccountRole::wireValue).orElse(null),
        row.lineClassification()
            .map(dev.erst.fingrind.core.FinancialPositionLineClassification::wireValue)
            .orElse(null),
        row.lineKind().wireValue(),
        CliPayloadAssembler.balancePayload(row.balance()));
  }

  static CliStatementJsonModels.IncomeStatementSectionPayload incomeStatementSectionPayload(
      IncomeStatementSection section) {
    return new CliStatementJsonModels.IncomeStatementSectionPayload(
        section.accountType().wireValue(),
        section.rows().stream().map(CliReportRowPayloadMapper::incomeStatementRowPayload).toList(),
        section.totals().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  static CliStatementJsonModels.IncomeStatementRowPayload incomeStatementRowPayload(
      IncomeStatementRow row) {
    return new CliStatementJsonModels.IncomeStatementRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().wireValue(),
        row.lineRole().map(dev.erst.fingrind.core.AccountRole::wireValue).orElse(null),
        row.lineClassification().wireValue(),
        row.lineKind().wireValue(),
        CliPayloadAssembler.balancePayload(row.movement()));
  }

  static CliStatementJsonModels.ChangesInEquityRowPayload changesInEquityRowPayload(
      ChangesInEquityRow row) {
    return new CliStatementJsonModels.ChangesInEquityRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().map(dev.erst.fingrind.core.AccountType::wireValue).orElse(null),
        row.lineRole().map(dev.erst.fingrind.core.AccountRole::wireValue).orElse(null),
        row.lineClassification()
            .map(dev.erst.fingrind.core.FinancialPositionLineClassification::wireValue)
            .orElse(null),
        row.lineKind().wireValue(),
        CliPayloadAssembler.balancePayload(row.openingBalance()),
        CliPayloadAssembler.balancePayload(row.movement()),
        CliPayloadAssembler.balancePayload(row.closingBalance()));
  }
}
