package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;

/** Lifecycle-register read capability exposed by {@link BookReadService}. */
public sealed interface BookReadLifecycleOperations permits BookReadService {
  /** Computes one durable accrual cut-off schedule through an optional effective-date cutoff. */
  default AccrualCutoffScheduleResult accrualCutoffSchedule(AccrualCutoffScheduleQuery query) {
    return ((BookReadService) this).lifecycleQueries().accrualCutoffSchedule(query);
  }

  /** Computes the durable fixed-asset register. */
  default FixedAssetRegisterResult fixedAssetRegister(FixedAssetRegisterQuery query) {
    return ((BookReadService) this).lifecycleQueries().fixedAssetRegister(query);
  }

  /** Computes the durable financing principal and interest register. */
  default FinancingRegisterResult financingRegister(FinancingRegisterQuery query) {
    return ((BookReadService) this).lifecycleQueries().financingRegister(query);
  }

  /** Computes the durable foreign-currency obligation and settlement register. */
  default RealizedForeignExchangeRegisterResult realizedForeignExchangeRegister(
      RealizedForeignExchangeRegisterQuery query) {
    return ((BookReadService) this).lifecycleQueries().realizedForeignExchangeRegister(query);
  }

  /** Computes the complete immutable Latvian payroll-run and settlement register. */
  default LatvianPayrollRegisterResult latvianPayrollRegister(LatvianPayrollRegisterQuery query) {
    return ((BookReadService) this).lifecycleQueries().latvianPayrollRegister(query);
  }
}
