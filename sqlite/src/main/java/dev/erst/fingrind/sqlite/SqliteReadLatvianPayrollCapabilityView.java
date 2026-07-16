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

/** Latvian payroll lifecycle defaults for SQLite read wrappers. */
interface SqliteReadLatvianPayrollCapabilityView
    extends LatvianPayrollLookupStore, SqlitePostingFactStoreReadOperationsView {
  @Override
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(
      LatvianPayrollRunId payrollRunId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().findRun(payrollRunId);
  }

  @Override
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
      PostingId originPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().findRunByOriginPosting(originPostingId);
  }

  @Override
  default Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().findActiveRun(employeeReference, payrollMonth);
  }

  @Override
  default List<LatvianPayrollRunRecord> latvianPayrollRuns() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().runs();
  }

  @Override
  default Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations()
        .latvianPayroll()
        .findActiveSettlement(payrollRunId, settlementKind);
  }

  @Override
  default Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
      PostingId originPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().findSettlementByPosting(originPostingId);
  }

  @Override
  default List<LatvianPayrollSettlementRecord> latvianPayrollSettlements() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latvianPayroll().settlements();
  }
}
