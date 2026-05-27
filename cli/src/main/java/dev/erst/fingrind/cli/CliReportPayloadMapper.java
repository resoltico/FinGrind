package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import org.jspecify.annotations.Nullable;

/** Maps report-domain payloads into CLI JSON report models. */
final class CliReportPayloadMapper {
  private CliReportPayloadMapper() {}

  static CliReportJsonModels.TrialBalancePayload trialBalancePayload(TrialBalanceReport report) {
    return new CliReportJsonModels.TrialBalancePayload(
        report.effectiveDateAsOf().map(Object::toString).orElse(null),
        reportContextPayload(
            report.bookIdentity(),
            report.postingCoverage(),
            comparativeRangeOrNull(
                CliReportSurfacePolicy.hasComparative(report),
                report.comparativeEffectiveDateRange())),
        report.rows().stream().map(CliReportPayloadMapper::trialBalanceRowPayload).toList(),
        report.totals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.balanced(),
        report.comparativeRows().stream()
            .map(CliReportPayloadMapper::trialBalanceRowPayload)
            .toList(),
        report.comparativeTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.comparativeBalanced());
  }

  static CliReportJsonModels.AccountLedgerPayload accountLedgerPayload(AccountLedgerReport report) {
    return new CliReportJsonModels.AccountLedgerPayload(
        reportContextPayload(report.bookIdentity(), report.postingCoverage()),
        report.account().accountCode().value(),
        report.account().accountName().value(),
        report.account().accountType().wireValue(),
        report.account().accountRole().wireValue(),
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
        reportContextPayload(report.bookIdentity(), report.postingCoverage()),
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

  static CliReportJsonModels.FinancialPositionPayload financialPositionPayload(
      FinancialPositionReport report) {
    return new CliReportJsonModels.FinancialPositionPayload(
        report.effectiveDateAsOf().map(Object::toString).orElse(null),
        reportContextPayload(
            report.bookIdentity(),
            report.postingCoverage(),
            comparativeRangeOrNull(
                CliReportSurfacePolicy.hasComparative(report),
                report.comparativeEffectiveDateRange())),
        report.sections().stream()
            .map(CliReportPayloadMapper::financialPositionSectionPayload)
            .toList(),
        report.comparativeSections().stream()
            .map(CliReportPayloadMapper::financialPositionSectionPayload)
            .toList());
  }

  static CliReportJsonModels.IncomeStatementPayload incomeStatementPayload(
      IncomeStatementReport report) {
    return new CliReportJsonModels.IncomeStatementPayload(
        report.effectiveDateFrom().toString(),
        report.effectiveDateTo().toString(),
        reportContextPayload(
            report.bookIdentity(),
            report.postingCoverage(),
            comparativeRangeOrNull(
                CliReportSurfacePolicy.hasComparative(report),
                report.comparativeEffectiveDateRange())),
        report.sections().stream()
            .map(CliReportPayloadMapper::incomeStatementSectionPayload)
            .toList(),
        report.netIncomeTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.comparativeSections().stream()
            .map(CliReportPayloadMapper::incomeStatementSectionPayload)
            .toList(),
        report.comparativeNetIncomeTotals().stream()
            .map(CliPayloadAssembler::balancePayload)
            .toList());
  }

  static CliReportJsonModels.ChangesInEquityPayload changesInEquityPayload(
      ChangesInEquityReport report) {
    return new CliReportJsonModels.ChangesInEquityPayload(
        report.effectiveDateFrom().toString(),
        report.effectiveDateTo().toString(),
        reportContextPayload(
            report.bookIdentity(),
            report.postingCoverage(),
            comparativeRangeOrNull(
                CliReportSurfacePolicy.hasComparative(report),
                report.comparativeEffectiveDateRange())),
        report.rows().stream().map(CliReportPayloadMapper::changesInEquityRowPayload).toList(),
        report.openingTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.movementTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.closingTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.comparativeRows().stream()
            .map(CliReportPayloadMapper::changesInEquityRowPayload)
            .toList(),
        report.comparativeOpeningTotals().stream()
            .map(CliPayloadAssembler::balancePayload)
            .toList(),
        report.comparativeMovementTotals().stream()
            .map(CliPayloadAssembler::balancePayload)
            .toList(),
        report.comparativeClosingTotals().stream()
            .map(CliPayloadAssembler::balancePayload)
            .toList());
  }

  private static CliReportJsonModels.TrialBalanceRowPayload trialBalanceRowPayload(
      TrialBalanceRow row) {
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

  private static CliReportJsonModels.AccountLedgerEntryPayload accountLedgerEntryPayload(
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

  private static CliReportJsonModels.PeriodAccountActivityPayload periodAccountActivityPayload(
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

  private static CliReportJsonModels.FinancialPositionSectionPayload
      financialPositionSectionPayload(FinancialPositionSection section) {
    return new CliReportJsonModels.FinancialPositionSectionPayload(
        section.accountType().wireValue(),
        section.rows().stream().map(CliReportPayloadMapper::financialPositionRowPayload).toList(),
        section.totals().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  private static CliReportJsonModels.FinancialPositionRowPayload financialPositionRowPayload(
      FinancialPositionRow row) {
    return new CliReportJsonModels.FinancialPositionRowPayload(
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

  private static CliReportJsonModels.IncomeStatementSectionPayload incomeStatementSectionPayload(
      IncomeStatementSection section) {
    return new CliReportJsonModels.IncomeStatementSectionPayload(
        section.accountType().wireValue(),
        section.rows().stream().map(CliReportPayloadMapper::incomeStatementRowPayload).toList(),
        section.totals().stream().map(CliPayloadAssembler::balancePayload).toList());
  }

  private static CliReportJsonModels.IncomeStatementRowPayload incomeStatementRowPayload(
      IncomeStatementRow row) {
    return new CliReportJsonModels.IncomeStatementRowPayload(
        row.lineCode(),
        row.lineName(),
        row.lineType().wireValue(),
        row.lineRole().map(dev.erst.fingrind.core.AccountRole::wireValue).orElse(null),
        row.lineClassification().wireValue(),
        row.lineKind().wireValue(),
        CliPayloadAssembler.balancePayload(row.movement()));
  }

  private static CliReportJsonModels.ChangesInEquityRowPayload changesInEquityRowPayload(
      ChangesInEquityRow row) {
    return new CliReportJsonModels.ChangesInEquityRowPayload(
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

  static CliReportJsonModels.ReportContextPayload reportContextPayload(
      BookIdentity bookIdentity, PostingCoverage postingCoverage) {
    return reportContextPayload(bookIdentity, postingCoverage, null);
  }

  static CliReportJsonModels.ReportContextPayload reportContextPayload(
      BookIdentity bookIdentity,
      PostingCoverage postingCoverage,
      @Nullable EffectiveDateRange comparativeEffectiveDateRange) {
    return new CliReportJsonModels.ReportContextPayload(
        CliBookInspectionPayloadMapper.bookIdentityPayload(bookIdentity),
        postingCoverage.wireValue(),
        comparativeEffectiveDateRange == null
            ? null
            : comparativeEffectiveDateRange.effectiveDateFrom().map(Object::toString).orElse(null),
        comparativeEffectiveDateRange == null
            ? null
            : comparativeEffectiveDateRange.effectiveDateTo().map(Object::toString).orElse(null));
  }

  private static @Nullable EffectiveDateRange comparativeRangeOrNull(
      boolean includeComparativeReference, EffectiveDateRange comparativeEffectiveDateRange) {
    return includeComparativeReference ? comparativeEffectiveDateRange : null;
  }
}
