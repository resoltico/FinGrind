package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
    return SqliteStatementQueries.findOneCommittedPosting(
        activeDatabase, sql, binder, postingId -> loadAttachments(activeDatabase, postingId));
  }

  List<CommittedPosting> loadCommittedPostings(
      SqliteNativeDatabase activeDatabase, String sql, SqliteStatementQueries.Binder binder) {
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
        new PostingId(SqlitePostingMapper.requiredText(statement, SqlitePostingSql.COL_POSTING_ID));
    SqliteStatementQueries.PostingAttachments attachments =
        loadAttachments(activeDatabase, postingId);
    return SqlitePostingMapper.committedPosting(
        statement, attachments.lines(), attachments.evidence());
  }

  private List<JournalLine> loadLines(SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try (SqliteNativeStatement statement = activeDatabase.prepare(SqlitePostingSql.LOAD_LINES)) {
      statement.bindText(1, postingId.value());
      return SqlitePostingMapper.journalLines(statement);
    }
  }

  private SqliteStatementQueries.PostingAttachments loadAttachments(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    return new SqliteStatementQueries.PostingAttachments(
        loadLines(activeDatabase, postingId), loadEvidence(activeDatabase, postingId));
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
}
