package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
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

/** Operational register reporting capability over one protected book. */
interface CliBookOperationalReportReadWorkflow {
  /** Reports exact per-account inventory valuation through an optional effective-date cutoff. */
  ContractDecision<InventoryValuationResult> inventoryValuation(
      BookAccess bookAccess, InventoryValuationQuery query);

  /**
   * Reports durable accrual cut-off lifecycle balances through an optional effective-date cutoff.
   */
  ContractDecision<AccrualCutoffScheduleResult> accrualCutoffSchedule(
      BookAccess bookAccess, AccrualCutoffScheduleQuery query);

  /** Reports the durable fixed-asset lifecycle register. */
  ContractDecision<FixedAssetRegisterResult> fixedAssetRegister(
      BookAccess bookAccess, FixedAssetRegisterQuery query);

  /** Reports the durable financing principal and interest register. */
  ContractDecision<FinancingRegisterResult> financingRegister(
      BookAccess bookAccess, FinancingRegisterQuery query);

  /** Reports the durable foreign-currency obligation and settlement register. */
  ContractDecision<RealizedForeignExchangeRegisterResult> realizedForeignExchangeRegister(
      BookAccess bookAccess, RealizedForeignExchangeRegisterQuery query);

  /** Reports the durable Latvian payroll calculation and settlement register. */
  ContractDecision<LatvianPayrollRegisterResult> latvianPayrollRegister(
      BookAccess bookAccess, LatvianPayrollRegisterQuery query);
}
