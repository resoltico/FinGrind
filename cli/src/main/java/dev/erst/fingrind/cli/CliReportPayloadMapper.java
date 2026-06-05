package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
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
        report.rows().stream().map(CliReportRowPayloadMapper::trialBalanceRowPayload).toList(),
        report.totals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.balanced(),
        report.comparativeRows().stream()
            .map(CliReportRowPayloadMapper::trialBalanceRowPayload)
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
            .map(
                entry ->
                    CliReportRowPayloadMapper.accountLedgerEntryPayload(report.account(), entry))
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
            .map(CliReportRowPayloadMapper::periodAccountActivityPayload)
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
            .map(CliReportRowPayloadMapper::financialPositionSectionPayload)
            .toList(),
        report.comparativeSections().stream()
            .map(CliReportRowPayloadMapper::financialPositionSectionPayload)
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
            .map(CliReportRowPayloadMapper::incomeStatementSectionPayload)
            .toList(),
        report.netIncomeTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.comparativeSections().stream()
            .map(CliReportRowPayloadMapper::incomeStatementSectionPayload)
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
        report.rows().stream().map(CliReportRowPayloadMapper::changesInEquityRowPayload).toList(),
        report.openingTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.movementTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.closingTotals().stream().map(CliPayloadAssembler::balancePayload).toList(),
        report.comparativeRows().stream()
            .map(CliReportRowPayloadMapper::changesInEquityRowPayload)
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
