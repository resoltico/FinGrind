package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.contract.payroll.LatvianPayrollWithholdingProfile;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite mapping for retained Latvian monthly-payroll run facts. */
final class SqliteLatvianPayrollStatementQueries {
  private SqliteLatvianPayrollStatementQueries() {}

  static Optional<LatvianPayrollRunRecord> findRun(
      SqliteNativeDatabase activeDatabase, LatvianPayrollRunId payrollRunId) {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    return findOne(
        activeDatabase,
        SqliteLatvianPayrollSql.FIND_RUN_BY_ID,
        statement -> statement.bindText(1, payrollRunId.value()));
  }

  static Optional<LatvianPayrollRunRecord> findRunByOriginPosting(
      SqliteNativeDatabase activeDatabase, PostingId originPostingId) {
    Objects.requireNonNull(originPostingId, "originPostingId");
    return findOne(
        activeDatabase,
        SqliteLatvianPayrollSql.FIND_RUN_BY_ORIGIN_POSTING_ID,
        statement -> statement.bindText(1, originPostingId.value()));
  }

  static Optional<LatvianPayrollRunRecord> findActiveRun(
      SqliteNativeDatabase activeDatabase,
      LatvianPayrollEmployeeReference employeeReference,
      LatvianPayrollMonth payrollMonth) {
    Objects.requireNonNull(employeeReference, "employeeReference");
    Objects.requireNonNull(payrollMonth, "payrollMonth");
    return findOne(
        activeDatabase,
        SqliteLatvianPayrollSql.FIND_ACTIVE_RUN_BY_EMPLOYEE_MONTH,
        statement -> {
          statement.bindText(1, employeeReference.value());
          statement.bindText(2, payrollMonth.wireValue());
        });
  }

  static List<LatvianPayrollRunRecord> loadRuns(SqliteNativeDatabase activeDatabase) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteLatvianPayrollSql.LIST_RUNS,
        statement -> {
          List<LatvianPayrollRunRecord> runs = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            runs.add(run(statement));
          }
          return List.copyOf(runs);
        });
  }

  static Optional<LatvianPayrollSettlementRecord> findActiveSettlement(
      SqliteNativeDatabase activeDatabase,
      LatvianPayrollRunId payrollRunId,
      LatvianPayrollSettlementKind settlementKind) {
    Objects.requireNonNull(payrollRunId, "payrollRunId");
    Objects.requireNonNull(settlementKind, "settlementKind");
    return findOneSettlement(
        activeDatabase,
        SqliteLatvianPayrollSql.FIND_ACTIVE_SETTLEMENT,
        statement -> {
          statement.bindText(1, payrollRunId.value());
          statement.bindText(2, settlementKind.wireValue());
        });
  }

  static Optional<LatvianPayrollSettlementRecord> findSettlementByOriginPosting(
      SqliteNativeDatabase activeDatabase, PostingId originPostingId) {
    Objects.requireNonNull(originPostingId, "originPostingId");
    return findOneSettlement(
        activeDatabase,
        SqliteLatvianPayrollSql.FIND_SETTLEMENT_BY_ORIGIN_POSTING_ID,
        statement -> statement.bindText(1, originPostingId.value()));
  }

  static List<LatvianPayrollSettlementRecord> loadSettlements(SqliteNativeDatabase activeDatabase) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteLatvianPayrollSql.LIST_SETTLEMENTS,
        statement -> {
          List<LatvianPayrollSettlementRecord> settlements = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            settlements.add(settlement(statement));
          }
          return List.copyOf(settlements);
        });
  }

  private static Optional<LatvianPayrollRunRecord> findOne(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          LatvianPayrollRunRecord run = run(statement);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite Latvian payroll lookup returned more than one row.");
          }
          return Optional.of(run);
        });
  }

  private static Optional<LatvianPayrollSettlementRecord> findOneSettlement(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        sql,
        statement -> {
          binder.bind(statement);
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          LatvianPayrollSettlementRecord settlement = settlement(statement);
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite Latvian payroll settlement lookup returned more than one row.");
          }
          return Optional.of(settlement);
        });
  }

  private static LatvianPayrollRunRecord run(SqliteNativeStatement statement) {
    CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 12));
    LatvianMonthlyPayrollCalculation calculation =
        new LatvianMonthlyPayrollCalculation(
            Money.ofMinorUnits(currency, statement.columnLong(13)),
            Money.ofMinorUnits(currency, statement.columnLong(14)),
            Money.ofMinorUnits(currency, statement.columnLong(15)),
            Money.ofMinorUnits(currency, statement.columnLong(16)),
            Money.ofMinorUnits(currency, statement.columnLong(17)),
            Money.ofMinorUnits(currency, statement.columnLong(18)),
            withholdingProfile(statement));
    return new LatvianPayrollRunRecord(
        new LatvianPayrollRunId(SqlitePostingMapper.requiredText(statement, 0)),
        new LatvianPayrollEmployeeReference(SqlitePostingMapper.requiredText(statement, 1)),
        LatvianPayrollMonth.parse(SqlitePostingMapper.requiredText(statement, 2)),
        CanonicalTemporalText.parseLocalDate(
            SqlitePostingMapper.requiredText(statement, 5), "latvianPayrollRun.effectiveDate"),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 6)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 7)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 8)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 9)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 10)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 11)),
        calculation,
        new PostingId(SqlitePostingMapper.requiredText(statement, 19)),
        Optional.ofNullable(statement.columnText(20)).map(PostingId::new));
  }

  private static LatvianPayrollSettlementRecord settlement(SqliteNativeStatement statement) {
    return new LatvianPayrollSettlementRecord(
        LatvianPayrollSettlementKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 0)),
        new LatvianPayrollRunId(SqlitePostingMapper.requiredText(statement, 1)),
        new PostingId(SqlitePostingMapper.requiredText(statement, 2)),
        CanonicalTemporalText.parseLocalDate(
            SqlitePostingMapper.requiredText(statement, 3),
            "latvianPayrollSettlement.effectiveDate"),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 4)),
        Optional.ofNullable(statement.columnText(5)).map(PostingId::new));
  }

  private static LatvianPayrollWithholdingProfile withholdingProfile(
      SqliteNativeStatement statement) {
    long taxBookHeldAtEmployer = statement.columnLong(3);
    long dependantCount = statement.columnLong(4);
    if (taxBookHeldAtEmployer != 1 || dependantCount != 0) {
      throw new IllegalStateException(
          "Stored Latvian payroll run has unsupported withholding-profile facts.");
    }
    return LatvianPayrollWithholdingProfile.taxBookWithNoDependantsFor2026();
  }
}
