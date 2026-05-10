package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared SQLite write helpers for persisted book metadata, accounts, and postings. */
final class SqliteMutationWriter {
  private SqliteMutationWriter() {}

  static void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt) {
    insertBookMetaValue(
        activeDatabase, SqlitePostingSql.INITIALIZED_AT_META_KEY, initializedAt.toString());
  }

  static void insertBookMetaValue(SqliteNativeDatabase activeDatabase, String key, String value) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, key);
      statement.bindText(2, value);
      statement.step();
    }
  }

  static void upsertAccount(SqliteNativeDatabase activeDatabase, RegisteredAccount account) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.UPSERT_ACCOUNT)) {
      statement.bindText(1, account.accountCode().value());
      statement.bindText(2, account.accountName().value());
      statement.bindText(3, account.normalBalance().wireValue());
      statement.bindInt(4, Boolean.compare(account.active(), false));
      statement.bindText(5, account.declaredAt().toString());
      statement.step();
    }
  }

  static void insertPostingFact(SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    RequestProvenance requestProvenance = postingFact.provenance().requestProvenance();
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_POSTING_FACT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, postingFact.journalEntry().effectiveDate().toString());
      statement.bindText(3, postingFact.provenance().recordedAt().toString());
      statement.bindText(4, requestProvenance.actorId().value());
      statement.bindText(5, requestProvenance.actorType().wireValue());
      statement.bindText(6, requestProvenance.commandId().value());
      statement.bindText(7, requestProvenance.idempotencyKey().value());
      statement.bindText(8, requestProvenance.causationId().value());
      bindOptionalText(
          statement, 9, requestProvenance.correlationId().map(value -> value.value()).orElse(null));
      bindOptionalText(
          statement,
          10,
          postingFact.postingLineage().reversalReason().map(value -> value.value()).orElse(null));
      statement.bindText(11, postingFact.provenance().sourceChannel().wireValue());
      bindOptionalText(
          statement,
          12,
          postingFact
              .postingLineage()
              .reversalReference()
              .map(reference -> reference.priorPostingId().value())
              .orElse(null));
      statement.step();
    }
  }

  static void insertJournalLines(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    preparePendingJournalLineTable(activeDatabase);
    clearPendingJournalLineTable(activeDatabase);
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_PENDING_JOURNAL_LINE)) {
        statement.bindInt(1, index);
        statement.bindText(2, line.accountCode().value());
        statement.bindText(3, line.side().wireValue());
        SqlitePersistedMoneyCodec.bindPositiveMoney(statement, 4, 5, line.amount());
        statement.step();
      }
    }
    requireBalancedPendingJournalLineTable(activeDatabase);
    persistPendingJournalLineTable(activeDatabase, postingFact.postingId().value());
    clearPendingJournalLineTable(activeDatabase);
  }

  private static void preparePendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    activeDatabase.executeStatement(SqlitePostingSql.CREATE_PENDING_JOURNAL_LINE);
  }

  private static void clearPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    activeDatabase.executeStatement(SqlitePostingSql.CLEAR_PENDING_JOURNAL_LINE);
  }

  private static void requireBalancedPendingJournalLineTable(SqliteNativeDatabase activeDatabase) {
    boolean validPendingLines =
        SqliteStatementQueries.existsRow(
            activeDatabase, SqlitePostingSql.VALID_PENDING_JOURNAL_LINE, statement -> {});
    if (!validPendingLines) {
      throw new IllegalStateException(
          "SQLite journal-line staging rejected one unbalanced or malformed posting.");
    }
  }

  private static void persistPendingJournalLineTable(
      SqliteNativeDatabase activeDatabase, String postingId) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.PERSIST_PENDING_JOURNAL_LINE)) {
      statement.bindText(1, postingId);
      statement.step();
    }
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value) {
    statement.bindText(parameterIndex, value);
  }
}
