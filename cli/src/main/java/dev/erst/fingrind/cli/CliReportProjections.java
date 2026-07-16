package dev.erst.fingrind.cli;

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
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccrualCutoffScheduleReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancingRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FixedAssetRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.LatvianPayrollRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.RealizedForeignExchangeRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;

/** Owns the model and payload projections for every standard bookkeeping report family. */
final class CliReportProjections {
  static final CliReportProjection<AccountBalanceSnapshot> ACCOUNT_BALANCE =
      new CliReportProjection<>(
          AccountBalanceReportModelBuilder::buildModel, CliReportPayloadMapper::accountBalance);
  static final CliReportProjection<TrialBalanceReport> TRIAL_BALANCE =
      new CliReportProjection<>(
          TrialBalanceReportModelBuilder::buildModel, CliReportPayloadMapper::trialBalance);
  static final CliReportProjection<AccountLedgerReport> ACCOUNT_LEDGER =
      new CliReportProjection<>(
          AccountLedgerReportModelBuilder::buildModel, CliReportPayloadMapper::accountLedger);
  static final CliReportProjection<PeriodSummaryReport> PERIOD_SUMMARY =
      new CliReportProjection<>(
          PeriodSummaryReportModelBuilder::buildModel, CliReportPayloadMapper::periodSummary);
  static final CliReportProjection<FinancialPositionReport> FINANCIAL_POSITION =
      new CliReportProjection<>(
          FinancialPositionReportModelBuilder::buildModel,
          CliReportPayloadMapper::financialPosition);
  static final CliReportProjection<IncomeStatementReport> INCOME_STATEMENT =
      new CliReportProjection<>(
          IncomeStatementReportModelBuilder::buildModel, CliReportPayloadMapper::incomeStatement);
  static final CliReportProjection<InventoryValuationReport> INVENTORY_VALUATION =
      new CliReportProjection<>(
          InventoryValuationReportModelBuilder::buildModel,
          CliReportPayloadMapper::inventoryValuation);
  static final CliReportProjection<AccrualCutoffScheduleReport> ACCRUAL_CUTOFF_SCHEDULE =
      new CliReportProjection<>(
          AccrualCutoffScheduleReportModelBuilder::buildModel,
          CliReportPayloadMapper::accrualCutoffSchedule);
  static final CliReportProjection<FixedAssetRegisterReport> FIXED_ASSET_REGISTER =
      new CliReportProjection<>(
          FixedAssetRegisterReportModelBuilder::buildModel,
          CliReportPayloadMapper::fixedAssetRegister);
  static final CliReportProjection<FinancingRegisterReport> FINANCING_REGISTER =
      new CliReportProjection<>(
          FinancingRegisterReportModelBuilder::buildModel,
          CliReportPayloadMapper::financingRegister);
  static final CliReportProjection<RealizedForeignExchangeRegisterReport>
      REALIZED_FOREIGN_EXCHANGE_REGISTER =
          new CliReportProjection<>(
              RealizedForeignExchangeRegisterReportModelBuilder::buildModel,
              CliReportPayloadMapper::realizedForeignExchangeRegister);
  static final CliReportProjection<LatvianPayrollRegisterReport> LATVIAN_PAYROLL_REGISTER =
      new CliReportProjection<>(
          LatvianPayrollRegisterReportModelBuilder::buildModel,
          CliReportPayloadMapper::latvianPayrollRegister);
  static final CliReportProjection<CashFlowStatementReport> CASH_FLOW_STATEMENT =
      new CliReportProjection<>(
          CashFlowStatementReportModelBuilder::buildModel,
          CliReportPayloadMapper::cashFlowStatement);
  static final CliReportProjection<ChangesInEquityReport> CHANGES_IN_EQUITY =
      new CliReportProjection<>(
          ChangesInEquityReportModelBuilder::buildModel, CliReportPayloadMapper::changesInEquity);

  private CliReportProjections() {}
}
