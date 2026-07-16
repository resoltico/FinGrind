package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.reportmodel.AccrualCutoffScheduleReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancingRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FixedAssetRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.InventoryValuationReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.LatvianPayrollRegisterReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.RealizedForeignExchangeRegisterReportModelBuilder;

/** Builds handlers for operational reports that are not financial statements. */
final class CliOperationalReportCommandHandlers {
  private CliOperationalReportCommandHandlers() {}

  static CliConfiguredReportHandler<
          InventoryValuationQuery, InventoryValuationResult, InventoryValuationReport>
      inventoryValuation(CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::inventoryValuation,
        InventoryValuationReportModelBuilder::buildModel,
        responseWriter::writeInventoryValuationResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          FixedAssetRegisterQuery, FixedAssetRegisterResult, FixedAssetRegisterReport>
      fixedAssetRegister(CliBookReadWorkflow workflow, CliReportResponseWriter writer) {
    return CliReportCommandCatalog.configured(
        workflow::fixedAssetRegister,
        FixedAssetRegisterReportModelBuilder::buildModel,
        writer::writeFixedAssetRegisterResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          FinancingRegisterQuery, FinancingRegisterResult, FinancingRegisterReport>
      financingRegister(CliBookReadWorkflow workflow, CliReportResponseWriter writer) {
    return CliReportCommandCatalog.configured(
        workflow::financingRegister,
        FinancingRegisterReportModelBuilder::buildModel,
        writer::writeFinancingRegisterResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          RealizedForeignExchangeRegisterQuery,
          RealizedForeignExchangeRegisterResult,
          RealizedForeignExchangeRegisterReport>
      realizedForeignExchangeRegister(
          CliBookReadWorkflow workflow, CliReportResponseWriter writer) {
    return CliReportCommandCatalog.configured(
        workflow::realizedForeignExchangeRegister,
        RealizedForeignExchangeRegisterReportModelBuilder::buildModel,
        writer::writeRealizedForeignExchangeRegisterResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          AccrualCutoffScheduleQuery, AccrualCutoffScheduleResult, AccrualCutoffScheduleReport>
      accrualCutoffSchedule(
          CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::accrualCutoffSchedule,
        AccrualCutoffScheduleReportModelBuilder::buildModel,
        responseWriter::writeAccrualCutoffScheduleResult,
        CliReportExitCodes::exitCodeFor);
  }

  static CliConfiguredReportHandler<
          LatvianPayrollRegisterQuery, LatvianPayrollRegisterResult, LatvianPayrollRegisterReport>
      latvianPayrollRegister(
          CliBookReadWorkflow readWorkflow, CliReportResponseWriter responseWriter) {
    return CliReportCommandCatalog.configured(
        readWorkflow::latvianPayrollRegister,
        LatvianPayrollRegisterReportModelBuilder::buildModel,
        responseWriter::writeLatvianPayrollRegisterResult,
        CliReportExitCodes::exitCodeFor);
  }
}
