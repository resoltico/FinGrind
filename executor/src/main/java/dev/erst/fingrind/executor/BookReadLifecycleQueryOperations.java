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
import java.util.Objects;

/** Application ownership for durable lifecycle-register queries. */
final class BookReadLifecycleQueryOperations {
  private final BookReportService bookReportService;

  BookReadLifecycleQueryOperations(BookReportService bookReportService) {
    this.bookReportService = Objects.requireNonNull(bookReportService, "bookReportService");
  }

  AccrualCutoffScheduleResult accrualCutoffSchedule(AccrualCutoffScheduleQuery query) {
    return bookReportService.accrualCutoffSchedule(query);
  }

  FixedAssetRegisterResult fixedAssetRegister(FixedAssetRegisterQuery query) {
    return bookReportService.fixedAssetRegister(query);
  }

  FinancingRegisterResult financingRegister(FinancingRegisterQuery query) {
    return bookReportService.financingRegister(query);
  }

  RealizedForeignExchangeRegisterResult realizedForeignExchangeRegister(
      RealizedForeignExchangeRegisterQuery query) {
    return bookReportService.realizedForeignExchangeRegister(query);
  }

  LatvianPayrollRegisterResult latvianPayrollRegister(LatvianPayrollRegisterQuery query) {
    return bookReportService.latvianPayrollRegister(query);
  }
}
