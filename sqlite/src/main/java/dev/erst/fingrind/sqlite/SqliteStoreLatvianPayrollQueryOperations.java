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
import java.util.function.Function;

/** Transaction-safe Latvian payroll lifecycle reads over an initialized SQLite book. */
final class SqliteStoreLatvianPayrollQueryOperations {
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreLatvianPayrollQueryOperations(SqliteStoreLifecycle lifecycle) {
    this.lifecycle = lifecycle;
  }

  Optional<LatvianPayrollRunRecord> findRun(LatvianPayrollRunId payrollRunId) {
    return query(
        "Failed to query SQLite Latvian payroll run.",
        activeDatabase ->
            SqliteLatvianPayrollStatementQueries.findRun(activeDatabase, payrollRunId));
  }

  Optional<LatvianPayrollRunRecord> findRunByOriginPosting(PostingId originPostingId) {
    return query(
        "Failed to query SQLite Latvian payroll run.",
        activeDatabase ->
            SqliteLatvianPayrollStatementQueries.findRunByOriginPosting(
                activeDatabase, originPostingId));
  }

  Optional<LatvianPayrollRunRecord> findActiveRun(
      LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
    return query(
        "Failed to query SQLite Latvian payroll run.",
        activeDatabase ->
            SqliteLatvianPayrollStatementQueries.findActiveRun(
                activeDatabase, employeeReference, payrollMonth));
  }

  List<LatvianPayrollRunRecord> runs() {
    return query(
        "Failed to query SQLite Latvian payroll runs.",
        SqliteLatvianPayrollStatementQueries::loadRuns);
  }

  Optional<LatvianPayrollSettlementRecord> findActiveSettlement(
      LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
    return query(
        "Failed to query SQLite Latvian payroll settlement.",
        activeDatabase ->
            SqliteLatvianPayrollStatementQueries.findActiveSettlement(
                activeDatabase, payrollRunId, settlementKind));
  }

  Optional<LatvianPayrollSettlementRecord> findSettlementByPosting(PostingId originPostingId) {
    return query(
        "Failed to query SQLite Latvian payroll settlement.",
        activeDatabase ->
            SqliteLatvianPayrollStatementQueries.findSettlementByOriginPosting(
                activeDatabase, originPostingId));
  }

  List<LatvianPayrollSettlementRecord> settlements() {
    return query(
        "Failed to query SQLite Latvian payroll settlements.",
        SqliteLatvianPayrollStatementQueries::loadSettlements);
  }

  private <T> T query(String failureMessage, Function<SqliteNativeDatabase, T> query) {
    lifecycle.ensureOpenSession();
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () -> query.apply(lifecycle.initializedQueryDatabase()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
