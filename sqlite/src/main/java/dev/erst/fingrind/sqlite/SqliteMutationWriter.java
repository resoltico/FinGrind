package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.RequestProvenance;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared SQLite write helpers for persisted book metadata, accounts, and postings. */
final class SqliteMutationWriter {
  private SqliteMutationWriter() {}

  static void insertInitializedAt(SqliteNativeDatabase activeDatabase, Instant initializedAt)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_BOOK_INITIALIZED_AT)) {
      statement.bindText(1, SqlitePostingSql.INITIALIZED_AT_META_KEY);
      statement.bindText(2, initializedAt.toString());
      statement.step();
    }
  }

  static void upsertAccount(SqliteNativeDatabase activeDatabase, DeclaredAccount account)
      throws SqliteNativeException {
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

  static void insertPostingFact(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
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

  static void insertJournalLines(SqliteNativeDatabase activeDatabase, PostingFact postingFact)
      throws SqliteNativeException {
    List<JournalLine> lines = postingFact.journalEntry().lines();
    for (int index = 0; index < lines.size(); index++) {
      JournalLine line = lines.get(index);
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqlitePostingSql.INSERT_JOURNAL_LINE)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindInt(2, index);
        statement.bindText(3, line.accountCode().value());
        statement.bindText(4, line.side().wireValue());
        statement.bindText(5, line.amount().currencyCode().value());
        statement.bindText(6, line.amount().amount().toPlainString());
        statement.step();
      }
    }
  }

  private static void bindOptionalText(
      SqliteNativeStatement statement, int parameterIndex, @Nullable String value)
      throws SqliteNativeException {
    statement.bindText(parameterIndex, value);
  }
}
