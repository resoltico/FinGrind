package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import java.util.List;
import java.util.Optional;

/** Transaction-scoped Latvian payroll lifecycle lookups used during posting validation. */
final class SqliteTransactionValidationPayrollQueries {
  private final SqliteNativeDatabase activeDatabase;

  SqliteTransactionValidationPayrollQueries(SqliteNativeDatabase activeDatabase) {
    this.activeDatabase = activeDatabase;
  }

  Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(LatvianPayrollRunId payrollRunId) {
    try {
      return SqliteLatvianPayrollStatementQueries.findRun(activeDatabase, payrollRunId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll run.", exception);
    }
  }

  Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
      PostingId originPostingId) {
    try {
      return SqliteLatvianPayrollStatementQueries.findRunByOriginPosting(
          activeDatabase, originPostingId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll run.", exception);
    }
  }

  Optional<LatvianPayrollRunRecord> findActiveLatvianPayrollRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    try {
      return SqliteLatvianPayrollStatementQueries.findActiveRun(
          activeDatabase, employeeReference, payrollMonth);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll run.", exception);
    }
  }

  List<LatvianPayrollRunRecord> latvianPayrollRuns() {
    try {
      return SqliteLatvianPayrollStatementQueries.loadRuns(activeDatabase);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll runs.", exception);
    }
  }

  Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    try {
      return SqliteLatvianPayrollStatementQueries.findActiveSettlement(
          activeDatabase, payrollRunId, settlementKind);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll settlement.", exception);
    }
  }

  Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
      PostingId originPostingId) {
    try {
      return SqliteLatvianPayrollStatementQueries.findSettlementByOriginPosting(
          activeDatabase, originPostingId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll settlement.", exception);
    }
  }

  List<LatvianPayrollSettlementRecord> latvianPayrollSettlements() {
    try {
      return SqliteLatvianPayrollStatementQueries.loadSettlements(activeDatabase);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite Latvian payroll settlements.", exception);
    }
  }
}
