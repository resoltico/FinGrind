package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared SQLite read helpers for postings, posting lines, and evidence attachments. */
final class SqlitePostingReader {
  PostingHistoryPage loadPostingPage(
      SqliteNativeDatabase activeDatabase, PostingHistoryQuery query) {
    List<CommittedPosting> postings = new ArrayList<>();
    String sql = SqlitePostingSql.listPostings(query);
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      bindPostingPageQuery(statement, query);
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    boolean hasMore = postings.size() > query.limit();
    List<CommittedPosting> pageItems = hasMore ? postings.subList(0, query.limit()) : postings;
    Optional<PostingHistoryCursor> nextCursor =
        hasMore ? Optional.of(postingHistoryCursor(pageItems.getLast())) : Optional.empty();
    return new PostingHistoryPage(pageItems, query.limit(), nextCursor);
  }

  Optional<CommittedPosting> findOneCommittedPosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    return SqliteStatementQueries.findOneCommittedPosting(
        activeDatabase, sql, binder, postingId -> loadAttachments(activeDatabase, postingId));
  }

  Optional<StoredRequestPosting> findOneStoredRequestPosting(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    return SqliteStatementQueries.findOneStoredRequestPosting(
        activeDatabase, sql, binder, postingId -> loadAttachments(activeDatabase, postingId));
  }

  List<CommittedPosting> loadCommittedPostings(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementBinder binder) {
    List<CommittedPosting> postings = new ArrayList<>();
    try (SqliteNativeStatement statement = activeDatabase.prepare(sql)) {
      binder.bind(statement);
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        postings.add(loadPostingRow(activeDatabase, statement));
      }
    }
    return List.copyOf(postings);
  }

  private CommittedPosting loadPostingRow(
      SqliteNativeDatabase activeDatabase, SqliteNativeStatement statement) {
    PostingId postingId =
        new PostingId(
            SqlitePostingMapper.requiredText(statement, SqlitePostingColumnIndexes.COL_POSTING_ID));
    SqlitePostingAttachments attachments = loadAttachments(activeDatabase, postingId);
    return SqlitePostingMapper.committedPosting(
        activeDatabase,
        statement,
        attachments.lines(),
        attachments.evidence(),
        attachments.appliedTax(),
        attachments.foreignExchangeDetails());
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private SqlitePostingAttachments loadAttachments(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    return new SqlitePostingAttachments(
        loadLines(activeDatabase, postingId),
        loadEvidence(activeDatabase, postingId),
        loadAppliedTax(activeDatabase, postingId),
        loadForeignExchange(activeDatabase, postingId));
  }

  private dev.erst.fingrind.core.AccountingEvidence loadEvidence(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement sourceDocumentRows =
            activeDatabase.prepare(SqlitePostingSql.LOAD_SOURCE_DOCUMENTS);
        SqliteNativeStatement approvalRows =
            activeDatabase.prepare(SqlitePostingSql.LOAD_APPROVALS)) {
      sourceDocumentRows.bindText(1, postingId.value());
      approvalRows.bindText(1, postingId.value());
      return SqlitePostingMapper.accountingEvidence(sourceDocumentRows, approvalRows);
    }
  }

  private @Nullable AppliedTax loadAppliedTax(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    AppliedTaxStatementScope statementScope = AppliedTaxStatementScope.open(activeDatabase);
    RuntimeException logicalFailure = null;
    AppliedTax appliedTax = null;
    try {
      statementScope.bindPostingId(postingId);
      if (statementScope.step() == SqliteNativeResultCode.code("ROW")) {
        appliedTax = statementScope.readAppliedTax();
        if (statementScope.step() != SqliteNativeResultCode.code("DONE")) {
          logicalFailure =
              new IllegalStateException(
                  "SQLite posting applied-tax query returned more than one row for posting "
                      + postingId.value()
                      + ".");
        }
      }
    } catch (RuntimeException | Error exception) {
      statementScope.closeAfterFailure(exception);
      throw exception;
    }
    if (logicalFailure != null) {
      statementScope.closeAfterFailure(logicalFailure);
      throw logicalFailure;
    }
    statementScope.close();
    return appliedTax;
  }

  private @Nullable ForeignExchangeDetails loadForeignExchange(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    ForeignExchangeStatementScope statementScope =
        ForeignExchangeStatementScope.open(activeDatabase);
    RuntimeException logicalFailure = null;
    ForeignExchangeDetails foreignExchangeDetails = null;
    try {
      statementScope.bindPostingId(postingId);
      if (statementScope.step() == SqliteNativeResultCode.code("ROW")) {
        foreignExchangeDetails = statementScope.readForeignExchangeDetails();
        if (statementScope.step() != SqliteNativeResultCode.code("DONE")) {
          logicalFailure =
              new IllegalStateException(
                  "SQLite posting foreign-exchange query returned more than one row for posting "
                      + postingId.value()
                      + ".");
        }
      }
    } catch (RuntimeException | Error exception) {
      statementScope.closeAfterFailure(exception);
      throw exception;
    }
    if (logicalFailure != null) {
      statementScope.closeAfterFailure(logicalFailure);
      throw logicalFailure;
    }
    statementScope.close();
    return foreignExchangeDetails;
  }

  private static void bindPostingPageQuery(
      SqliteNativeStatement statement, PostingHistoryQuery query) {
    int bindIndex = 1;
    if (query.accountCode().isPresent()) {
      statement.bindText(bindIndex, query.accountCode().orElseThrow().value());
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateFrom().orElseThrow()));
      bindIndex++;
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      statement.bindText(
          bindIndex,
          CanonicalTemporalText.formatLocalDate(
              query.effectiveDateRange().effectiveDateTo().orElseThrow()));
      bindIndex++;
    }
    if (query.cursor().isPresent()) {
      PostingHistoryCursor cursor = query.cursor().orElseThrow();
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatUtcInstant(cursor.recordedAt()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatLocalDate(cursor.effectiveDate()));
      bindIndex++;
      statement.bindText(bindIndex, CanonicalTemporalText.formatUtcInstant(cursor.recordedAt()));
      bindIndex++;
      statement.bindText(bindIndex, cursor.postingId().value());
      bindIndex++;
    }
    statement.bindInt(bindIndex, query.limit() + 1);
  }

  private static PostingHistoryCursor postingHistoryCursor(CommittedPosting posting) {
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  /** Manually scoped applied-tax statement to avoid impossible TWR-only coverage branches. */
  private static final class AppliedTaxStatementScope {
    private final SqliteNativeStatement statement;

    private AppliedTaxStatementScope(SqliteNativeStatement statement) {
      this.statement = statement;
    }

    private static AppliedTaxStatementScope open(SqliteNativeDatabase activeDatabase) {
      return new AppliedTaxStatementScope(
          activeDatabase.prepare(SqliteTaxSql.LOAD_POSTING_APPLIED_TAX));
    }

    private void bindPostingId(PostingId postingId) {
      statement.bindText(1, postingId.value());
    }

    private int step() {
      return statement.step();
    }

    private AppliedTax readAppliedTax() {
      return SqliteTaxMapper.appliedTax(statement);
    }

    private void close() {
      statement.close();
    }

    private void closeAfterFailure(Throwable primaryFailure) {
      try {
        statement.close();
      } catch (RuntimeException | Error closeFailure) {
        primaryFailure.addSuppressed(closeFailure);
      }
    }
  }

  /** Manually scoped foreign-exchange statement to avoid impossible TWR-only coverage branches. */
  private static final class ForeignExchangeStatementScope {
    private final SqliteNativeStatement statement;

    private ForeignExchangeStatementScope(SqliteNativeStatement statement) {
      this.statement = statement;
    }

    private static ForeignExchangeStatementScope open(SqliteNativeDatabase activeDatabase) {
      return new ForeignExchangeStatementScope(
          activeDatabase.prepare(SqlitePostingSql.LOAD_POSTING_FOREIGN_EXCHANGE));
    }

    private void bindPostingId(PostingId postingId) {
      statement.bindText(1, postingId.value());
    }

    private int step() {
      return statement.step();
    }

    private ForeignExchangeDetails readForeignExchangeDetails() {
      return SqliteForeignExchangeMapper.details(statement);
    }

    private void close() {
      statement.close();
    }

    private void closeAfterFailure(Throwable primaryFailure) {
      try {
        statement.close();
      } catch (RuntimeException | Error closeFailure) {
        primaryFailure.addSuppressed(closeFailure);
      }
    }
  }
}
