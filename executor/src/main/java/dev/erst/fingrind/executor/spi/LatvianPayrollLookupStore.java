package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import java.util.List;
import java.util.Optional;

/** Loads immutable Latvian payroll runs for admission, reversal, and register reads. */
public interface LatvianPayrollLookupStore {
  /** Returns a durable run by its globally unique run identifier, including a reversed run. */
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(
      LatvianPayrollRunId payrollRunId) {
    throw unsupported("findLatvianPayrollRun");
  }

  /** Returns the current unreversed run for one employee and payroll month, when it exists. */
  default Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    throw unsupported("findActiveLatvianPayrollRun");
  }

  /** Returns durable payroll runs in their retained payroll-month and run-id order. */
  default List<LatvianPayrollRunRecord> latvianPayrollRuns() {
    throw unsupported("latvianPayrollRuns");
  }

  /** Returns the active settlement for one run obligation, when the obligation is discharged. */
  default Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    throw unsupported("findActiveLatvianPayrollSettlement");
  }

  /** Returns a settlement by its originating posting, including a reversed settlement. */
  default Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
      PostingId originPostingId) {
    throw unsupported("findLatvianPayrollSettlementByPosting");
  }

  /** Returns a payroll run by its originating posting, including a reversed run. */
  default Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
      PostingId originPostingId) {
    throw unsupported("findLatvianPayrollRunByOriginPosting");
  }

  /** Returns durable payroll settlements in run, obligation, and posting order. */
  default List<LatvianPayrollSettlementRecord> latvianPayrollSettlements() {
    throw unsupported("latvianPayrollSettlements");
  }

  private static UnsupportedOperationException unsupported(String operation) {
    return new UnsupportedOperationException(
        "Latvian payroll lookup operation '"
            + operation
            + "' requires an owned store implementation.");
  }
}
