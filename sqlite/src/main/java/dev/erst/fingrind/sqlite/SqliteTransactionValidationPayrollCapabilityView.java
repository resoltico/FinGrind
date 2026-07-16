package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import dev.erst.fingrind.executor.spi.LatvianPayrollLookupStore;
import java.util.List;
import java.util.Optional;

/** Latvian payroll lifecycle defaults for transaction-scoped posting validation. */
interface SqliteTransactionValidationPayrollCapabilityView extends LatvianPayrollLookupStore {
  @Override
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(
      LatvianPayrollRunId payrollRunId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .findLatvianPayrollRun(payrollRunId);
  }

  @Override
  default Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .findActiveLatvianPayrollRun(employeeReference, payrollMonth);
  }

  @Override
  default List<LatvianPayrollRunRecord> latvianPayrollRuns() {
    return SqliteTransactionValidationBook.requireOwner(this).payrollQueries().latvianPayrollRuns();
  }

  @Override
  default Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .findActiveLatvianPayrollSettlement(payrollRunId, settlementKind);
  }

  @Override
  default Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
      PostingId originPostingId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .findLatvianPayrollSettlementByPosting(originPostingId);
  }

  @Override
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
      PostingId originPostingId) {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .findLatvianPayrollRunByOriginPosting(originPostingId);
  }

  @Override
  default List<LatvianPayrollSettlementRecord> latvianPayrollSettlements() {
    return SqliteTransactionValidationBook.requireOwner(this)
        .payrollQueries()
        .latvianPayrollSettlements();
  }
}
