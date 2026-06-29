package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;

/** Shared SQLite write helpers for one append-only bookkeeping audit stream. */
final class SqliteAuditEventWriter {
  private SqliteAuditEventWriter() {}

  static void insertAuditEvent(SqliteNativeDatabase activeDatabase, BookAuditEvent auditEvent) {
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqlitePostingSql.INSERT_AUDIT_EVENT)) {
      statement.bindText(1, CanonicalTemporalText.formatUtcInstant(auditEvent.recordedAt()));
      statement.bindText(2, auditEvent.kind().wireValue());
      statement.bindText(
          3, auditEvent.accountCode() == null ? null : auditEvent.accountCode().value());
      statement.bindText(4, auditEvent.postingId() == null ? null : auditEvent.postingId().value());
      if (auditEvent.closeOperationOrder() == null) {
        statement.bindText(5, null);
      } else {
        statement.bindInt(5, auditEvent.closeOperationOrder());
      }
      statement.step();
    }
  }
}
