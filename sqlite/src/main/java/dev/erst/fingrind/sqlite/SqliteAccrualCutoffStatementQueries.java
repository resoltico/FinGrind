package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.AccrualCutoffKind;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite read mapping for accrual cut-off aggregate and application facts. */
final class SqliteAccrualCutoffStatementQueries {
  private SqliteAccrualCutoffStatementQueries() {}

  static Optional<AccrualCutoffRecord> findCutoff(
      SqliteNativeDatabase activeDatabase, AccrualCutoffId accrualCutoffId) {
    Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
    return findCutoff(
        activeDatabase,
        SqliteAccrualCutoffSql.FIND_CUTOFF_BY_ID,
        statement -> statement.bindText(1, accrualCutoffId.value()));
  }

  static Optional<AccrualCutoffRecord> findCutoffByOriginPosting(
      SqliteNativeDatabase activeDatabase, PostingId originPostingId) {
    Objects.requireNonNull(originPostingId, "originPostingId");
    return findCutoff(
        activeDatabase,
        SqliteAccrualCutoffSql.FIND_CUTOFF_BY_ORIGIN_POSTING_ID,
        statement -> statement.bindText(1, originPostingId.value()));
  }

  static List<AccrualCutoffRecord> loadCutoffs(
      SqliteNativeDatabase activeDatabase, Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    String sql =
        effectiveDateAsOf.isPresent()
            ? SqliteAccrualCutoffSql.LIST_CUTOFFS_AS_OF
            : SqliteAccrualCutoffSql.LIST_CUTOFFS;
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        sql,
        statement -> {
          if (effectiveDateAsOf.isPresent()) {
            String asOf = CanonicalTemporalText.formatLocalDate(effectiveDateAsOf.orElseThrow());
            statement.bindText(1, asOf);
            statement.bindText(2, asOf);
          }
          List<AccrualCutoffRecord> cutoffs = new ArrayList<>();
          while (statement.step() == SqliteNativeResultCode.code("ROW")) {
            cutoffs.add(cutoff(statement));
          }
          return List.copyOf(cutoffs);
        });
  }

  static Optional<ApplicationContext> findApplicationContext(
      SqliteNativeDatabase activeDatabase, PostingId applicationPostingId) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(applicationPostingId, "applicationPostingId");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteAccrualCutoffSql.FIND_APPLICATION_CONTEXT_BY_POSTING_ID,
        statement -> {
          statement.bindText(1, applicationPostingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          ApplicationContext context =
              new ApplicationContext(
                  new AccrualCutoffId(SqlitePostingMapper.requiredText(statement, 0)),
                  AccrualCutoffApplicationKind.fromWireValue(
                      SqlitePostingMapper.requiredText(statement, 1)));
          return Optional.of(context);
        });
  }

  static Optional<ApplicationReversalInput> findApplicationReversalInput(
      SqliteNativeDatabase activeDatabase, PostingId applicationPostingId) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(applicationPostingId, "applicationPostingId");
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteAccrualCutoffSql.FIND_APPLICATION_REVERSAL_INPUT_BY_POSTING_ID,
        statement -> {
          statement.bindText(1, applicationPostingId.value());
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          ApplicationReversalInput input =
              new ApplicationReversalInput(
                  new AccrualCutoffId(SqlitePostingMapper.requiredText(statement, 0)),
                  Money.ofMinorUnits(
                      CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 1)),
                      statement.columnLong(2)));
          return Optional.of(input);
        });
  }

  private static Optional<AccrualCutoffRecord> findCutoff(
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
          AccrualCutoffRecord cutoff = cutoff(statement);
          return Optional.of(cutoff);
        });
  }

  private static AccrualCutoffRecord cutoff(SqliteNativeStatement statement) {
    AccrualCutoffId accrualCutoffId =
        new AccrualCutoffId(SqlitePostingMapper.requiredText(statement, 0));
    AccrualCutoffKind kind =
        AccrualCutoffKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 1));
    LocalDate originatedOn =
        CanonicalTemporalText.parseLocalDate(
            SqlitePostingMapper.requiredText(statement, 2), "accrualCutoff.originatedOn");
    AccountCode cutoffAccountCode = new AccountCode(SqlitePostingMapper.requiredText(statement, 3));
    AccountCode recognitionAccountCode =
        new AccountCode(SqlitePostingMapper.requiredText(statement, 4));
    CurrencyUnit currencyUnit = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 5));
    Money originalAmount = Money.ofMinorUnits(currencyUnit, statement.columnLong(6));
    @org.jspecify.annotations.Nullable String recognitionStartDate = statement.columnText(7);
    @org.jspecify.annotations.Nullable String recognitionEndDate = statement.columnText(8);
    Money appliedAmount = Money.ofMinorUnits(currencyUnit, statement.columnLong(9));
    Optional<LocalDate> latestApplicationEffectiveDate =
        Optional.ofNullable(statement.columnText(10))
            .map(
                value ->
                    CanonicalTemporalText.parseLocalDate(
                        value, "accrualCutoff.latestApplicationEffectiveDate"));
    return switch (kind) {
      case PREPAYMENT ->
          new AccrualCutoffRecord.Prepayment(
              accrualCutoffId,
              originatedOn,
              cutoffAccountCode,
              recognitionAccountCode,
              originalAmount,
              recognitionInterval(
                  Objects.requireNonNull(recognitionStartDate, "recognitionStartDate"),
                  Objects.requireNonNull(recognitionEndDate, "recognitionEndDate")),
              appliedAmount,
              latestApplicationEffectiveDate);
      case DEFERRED_REVENUE ->
          new AccrualCutoffRecord.DeferredRevenue(
              accrualCutoffId,
              originatedOn,
              cutoffAccountCode,
              recognitionAccountCode,
              originalAmount,
              recognitionInterval(
                  Objects.requireNonNull(recognitionStartDate, "recognitionStartDate"),
                  Objects.requireNonNull(recognitionEndDate, "recognitionEndDate")),
              appliedAmount,
              latestApplicationEffectiveDate);
      case ACCRUED_EXPENSE ->
          new AccrualCutoffRecord.AccruedExpense(
              accrualCutoffId,
              originatedOn,
              cutoffAccountCode,
              recognitionAccountCode,
              originalAmount,
              appliedAmount,
              latestApplicationEffectiveDate);
    };
  }

  private static AccrualCutoffRecognitionInterval recognitionInterval(
      String recognitionStartDate, String recognitionEndDate) {
    return new AccrualCutoffRecognitionInterval(
        CanonicalTemporalText.parseLocalDate(
            Objects.requireNonNull(recognitionStartDate, "recognitionStartDate"),
            "accrualCutoff.recognitionStartDate"),
        CanonicalTemporalText.parseLocalDate(
            Objects.requireNonNull(recognitionEndDate, "recognitionEndDate"),
            "accrualCutoff.recognitionEndDate"));
  }

  record ApplicationContext(
      AccrualCutoffId accrualCutoffId, AccrualCutoffApplicationKind applicationKind) {
    ApplicationContext {
      Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
      Objects.requireNonNull(applicationKind, "applicationKind");
    }
  }

  record ApplicationReversalInput(AccrualCutoffId accrualCutoffId, Money amount) {
    ApplicationReversalInput {
      Objects.requireNonNull(accrualCutoffId, "accrualCutoffId");
      Objects.requireNonNull(amount, "amount");
      if (!amount.isPositive()) {
        throw new IllegalArgumentException(
            "Accrual-cutoff application reversal input amount must be positive.");
      }
    }
  }
}
