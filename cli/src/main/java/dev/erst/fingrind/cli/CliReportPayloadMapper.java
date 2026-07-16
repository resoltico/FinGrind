package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAccountReportJsonModels;
import dev.erst.fingrind.cli.json.CliAccrualCutoffReportJsonModels;
import dev.erst.fingrind.cli.json.CliFixedAssetReportJsonModels;
import dev.erst.fingrind.cli.json.CliInventoryReportJsonModels;
import dev.erst.fingrind.cli.json.CliLatvianPayrollReportJsonModels;
import dev.erst.fingrind.cli.json.CliLifecycleContextReportJsonModels;
import dev.erst.fingrind.cli.json.CliStatementReportJsonModels;
import dev.erst.fingrind.cli.json.CliTaxReportJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import java.time.Instant;

/** Entry point for family-owned semantic report payload projections. */
final class CliReportPayloadMapper {
  private CliReportPayloadMapper() {}

  static CliAccountReportJsonModels.AccountBalancePayload accountBalance(
      AccountBalanceSnapshot report, Instant generatedAt) {
    return CliAccountReportPayloadMapper.accountBalance(report, generatedAt);
  }

  static CliAccountReportJsonModels.TrialBalancePayload trialBalance(
      TrialBalanceReport report, Instant generatedAt) {
    return CliAccountReportPayloadMapper.trialBalance(report, generatedAt);
  }

  static CliAccountReportJsonModels.AccountLedgerPayload accountLedger(
      AccountLedgerReport report, Instant generatedAt) {
    return CliAccountReportPayloadMapper.accountLedger(report, generatedAt);
  }

  static CliAccountReportJsonModels.PeriodSummaryPayload periodSummary(
      PeriodSummaryReport report, Instant generatedAt) {
    return CliAccountReportPayloadMapper.periodSummary(report, generatedAt);
  }

  static CliStatementReportJsonModels.FinancialPositionPayload financialPosition(
      FinancialPositionReport report, Instant generatedAt) {
    return CliStatementReportPayloadMapper.financialPosition(report, generatedAt);
  }

  static CliStatementReportJsonModels.IncomeStatementPayload incomeStatement(
      IncomeStatementReport report, Instant generatedAt) {
    return CliStatementReportPayloadMapper.incomeStatement(report, generatedAt);
  }

  static CliInventoryReportJsonModels.InventoryValuationPayload inventoryValuation(
      InventoryValuationReport report, Instant generatedAt) {
    return CliInventoryReportPayloadMapper.inventoryValuation(report, generatedAt);
  }

  static CliAccrualCutoffReportJsonModels.AccrualCutoffSchedulePayload accrualCutoffSchedule(
      AccrualCutoffScheduleReport report, Instant generatedAt) {
    return CliAccrualCutoffReportPayloadMapper.schedule(report, generatedAt);
  }

  static CliFixedAssetReportJsonModels.FixedAssetRegisterPayload fixedAssetRegister(
      FixedAssetRegisterReport report, Instant generatedAt) {
    return CliFixedAssetReportPayloadMapper.register(report, generatedAt);
  }

  static CliLifecycleContextReportJsonModels.FinancingRegisterPayload financingRegister(
      FinancingRegisterReport report, Instant generatedAt) {
    return CliLifecycleContextReportPayloadMapper.financing(report, generatedAt);
  }

  static CliLifecycleContextReportJsonModels.RealizedForeignExchangeRegisterPayload
      realizedForeignExchangeRegister(
          RealizedForeignExchangeRegisterReport report, Instant generatedAt) {
    return CliLifecycleContextReportPayloadMapper.realizedForeignExchange(report, generatedAt);
  }

  static CliLatvianPayrollReportJsonModels.LatvianPayrollRegisterPayload latvianPayrollRegister(
      LatvianPayrollRegisterReport report, Instant generatedAt) {
    return CliLatvianPayrollReportPayloadMapper.register(report, generatedAt);
  }

  static CliStatementReportJsonModels.CashFlowStatementPayload cashFlowStatement(
      CashFlowStatementReport report, Instant generatedAt) {
    return CliStatementReportPayloadMapper.cashFlowStatement(report, generatedAt);
  }

  static CliStatementReportJsonModels.ChangesInEquityPayload changesInEquity(
      ChangesInEquityReport report, Instant generatedAt) {
    return CliStatementReportPayloadMapper.changesInEquity(report, generatedAt);
  }

  static CliTaxReportJsonModels.TaxObligationPayload taxObligation(
      TaxObligationReport report, Instant generatedAt) {
    return CliTaxReportPayloadMapper.taxObligation(report, generatedAt);
  }
}
