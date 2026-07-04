package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-side support for close mutations that need statement inputs and prior close markers. */
final class SqliteClosingMutationReadSupport {
  private final SqliteStoreContext context;

  SqliteClosingMutationReadSupport(SqliteStoreContext context) {
    this.context = context;
  }

  List<CommittedPosting> loadPostingsInRange(
      SqliteNativeDatabase activeDatabase, dev.erst.fingrind.core.EffectiveDateRange range) {
    return context
        .postingReader()
        .loadCommittedPostings(
            activeDatabase,
            SqlitePostingSql.LOAD_POSTINGS_IN_RANGE,
            statement -> {
              String effectiveDateFrom =
                  range
                      .effectiveDateFrom()
                      .map(CanonicalTemporalText::formatLocalDate)
                      .orElse(null);
              String effectiveDateTo =
                  range.effectiveDateTo().map(CanonicalTemporalText::formatLocalDate).orElse(null);
              statement.bindText(1, effectiveDateFrom);
              statement.bindText(2, effectiveDateFrom);
              statement.bindText(3, effectiveDateTo);
              statement.bindText(4, effectiveDateTo);
            });
  }

  Optional<LocalDate> loadTransferredThroughEffectiveDate(SqliteNativeDatabase activeDatabase) {
    return SqliteStatementQueries.loadOptionalText(
            activeDatabase,
            SqliteReportingPeriodCloseSql.FIND_CLOSED_THROUGH_EFFECTIVE_DATE,
            statement -> {})
        .map(
            text ->
                CanonicalTemporalText.parseLocalDate(text, "interimResultSweep.effectiveDateTo"));
  }

  Optional<LocalDate> loadLatestTransferredThroughEffectiveDateWithinPeriod(
      SqliteNativeDatabase activeDatabase, dev.erst.fingrind.core.ReportingPeriod reportingPeriod) {
    return SqliteStatementQueries.loadOptionalText(
            activeDatabase,
            SqliteReportingPeriodCloseSql.FIND_LATEST_CLOSED_THROUGH_WITHIN_PERIOD,
            statement -> {
              statement.bindText(
                  1, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateFrom()));
              statement.bindText(
                  2, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateTo()));
            })
        .map(
            text ->
                CanonicalTemporalText.parseLocalDate(text, "interimResultSweep.effectiveDateTo"));
  }

  Optional<ClosedFiscalYearRecord> loadFiscalYearClose(
      SqliteNativeDatabase activeDatabase, dev.erst.fingrind.core.ReportingPeriod reportingPeriod) {
    return SqliteStatementQueries.queryWithStatement(
        activeDatabase,
        SqliteReportingPeriodCloseSql.FIND_FISCAL_YEAR_CLOSE_BY_PERIOD,
        statement -> {
          statement.bindText(
              1, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateFrom()));
          statement.bindText(
              2, CanonicalTemporalText.formatLocalDate(reportingPeriod.effectiveDateTo()));
          if (statement.step() != SqliteNativeResultCode.code("ROW")) {
            return Optional.empty();
          }
          int closeOrder = statement.columnInt(0);
          ClosedFiscalYearRecord closedFiscalYear =
              new ClosedFiscalYearRecord(
                  closeOrder,
                  reportingPeriod,
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 1)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
                  new AccountCode(SqlitePostingMapper.requiredText(statement, 3)),
                  CanonicalTemporalText.parseUtcInstant(
                      SqlitePostingMapper.requiredText(statement, 4), "fiscalYearClose.closedAt"),
                  loadFiscalYearClosePostingIds(activeDatabase, closeOrder));
          if (statement.step() != SqliteNativeResultCode.code("DONE")) {
            throw new IllegalStateException(
                "SQLite fiscal-year close query returned more than one row for one reporting period.");
          }
          return Optional.of(closedFiscalYear);
        });
  }

  private static List<PostingId> loadFiscalYearClosePostingIds(
      SqliteNativeDatabase activeDatabase, int closeOrder) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteReportingPeriodCloseSql.FIND_FISCAL_YEAR_CLOSE_POSTING_IDS)) {
      statement.bindInt(1, closeOrder);
      List<PostingId> postingIds = new ArrayList<>();
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        postingIds.add(new PostingId(SqlitePostingMapper.requiredText(statement, 0)));
      }
      return List.copyOf(postingIds);
    }
  }
}
