package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Supplies focused PDF-artifact workflow outcomes for operational report families. */
final class CliPdfOperationalReportWorkflowFixtures {
  private CliPdfOperationalReportWorkflowFixtures() {}

  static CliBookWorkflow inventoryValuation(boolean succeeds) {
    InventoryValuationResult result =
        succeeds
            ? new InventoryValuationResult.Reported(
                ReportCrossFormatInventoryFixture.sampleInventoryValuationReport(true))
            : rejectedInventoryValuation();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<InventoryValuationResult> inventoryValuation(
          BookAccess bookAccess, InventoryValuationQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  static CliBookWorkflow accrualCutoffSchedule(boolean succeeds) {
    AccrualCutoffScheduleResult result =
        succeeds
            ? new AccrualCutoffScheduleResult.Reported(
                ReportCrossFormatAccrualCutoffFixture.sampleAccrualCutoffScheduleReport())
            : rejectedAccrualCutoffSchedule();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<AccrualCutoffScheduleResult> accrualCutoffSchedule(
          BookAccess bookAccess, AccrualCutoffScheduleQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  static CliBookWorkflow fixedAssetRegister(boolean succeeds) {
    FixedAssetRegisterResult result =
        succeeds
            ? new FixedAssetRegisterResult.Reported(
                ReportCrossFormatLifecycleContextFixture.fixedAssetRegisterReport())
            : rejectedFixedAssetRegister();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<FixedAssetRegisterResult> fixedAssetRegister(
          BookAccess bookAccess, FixedAssetRegisterQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  static CliBookWorkflow financingRegister(boolean succeeds) {
    FinancingRegisterResult result =
        succeeds
            ? new FinancingRegisterResult.Reported(
                ReportCrossFormatLifecycleContextFixture.financingRegisterReport())
            : rejectedFinancingRegister();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<FinancingRegisterResult> financingRegister(
          BookAccess bookAccess, FinancingRegisterQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  static CliBookWorkflow realizedForeignExchangeRegister(boolean succeeds) {
    RealizedForeignExchangeRegisterResult result =
        succeeds
            ? new RealizedForeignExchangeRegisterResult.Reported(
                ReportCrossFormatLifecycleContextFixture.realizedForeignExchangeRegisterReport())
            : rejectedRealizedForeignExchangeRegister();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<RealizedForeignExchangeRegisterResult>
          realizedForeignExchangeRegister(
              BookAccess bookAccess, RealizedForeignExchangeRegisterQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  static CliBookWorkflow latvianPayrollRegister(boolean succeeds) {
    LatvianPayrollRegisterResult result =
        succeeds
            ? new LatvianPayrollRegisterResult.Reported(
                ReportCrossFormatLatvianPayrollFixture.sampleLatvianPayrollRegisterReport())
            : rejectedLatvianPayrollRegister();
    return new CliBookWorkflowAdapter() {
      @Override
      public ContractDecision<LatvianPayrollRegisterResult> latvianPayrollRegister(
          BookAccess bookAccess, LatvianPayrollRegisterQuery query) {
        return CliWorkflowDoubleSupport.accepted(result);
      }
    };
  }

  private static InventoryValuationResult rejectedInventoryValuation() {
    return new InventoryValuationResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static AccrualCutoffScheduleResult rejectedAccrualCutoffSchedule() {
    return new AccrualCutoffScheduleResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static FixedAssetRegisterResult rejectedFixedAssetRegister() {
    return new FixedAssetRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static FinancingRegisterResult rejectedFinancingRegister() {
    return new FinancingRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }

  private static RealizedForeignExchangeRegisterResult rejectedRealizedForeignExchangeRegister() {
    return new RealizedForeignExchangeRegisterResult.Rejected(
        new BookQueryRejection.BookNotInitialized());
  }

  private static LatvianPayrollRegisterResult rejectedLatvianPayrollRegister() {
    return new LatvianPayrollRegisterResult.Rejected(new BookQueryRejection.BookNotInitialized());
  }
}
