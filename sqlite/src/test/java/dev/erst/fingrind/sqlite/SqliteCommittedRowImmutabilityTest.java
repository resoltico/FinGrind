package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Proves committed posting and audit rows are immutable at the durable schema boundary. */
class SqliteCommittedRowImmutabilityTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void postingFactJournalLineAndAuditEventRows_rejectDirectUpdateAndDelete() {
    Path bookPath = tempDirectory.resolve("committed-row-immutability.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          """
          update posting_fact
          set command_id = '019e26ff-0000-7002-8000-000000000009'
          where posting_id = '%s'
          """
              .formatted(TestPostingIds.valueForLabel("posting-1")),
          "posting_fact");
      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          "delete from posting_fact where posting_id = '%s'"
              .formatted(TestPostingIds.valueForLabel("posting-1")),
          "posting_fact");
      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          """
          update journal_line
          set amount_minor = 999
          where posting_id = '%s' and line_order = 0
          """
              .formatted(TestPostingIds.valueForLabel("posting-1")),
          "journal_line");
      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          "delete from journal_line where posting_id = '%s' and line_order = 0"
              .formatted(TestPostingIds.valueForLabel("posting-1")),
          "journal_line");
      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          """
          update audit_event
          set recorded_at = '2026-04-09T10:15:30Z'
          where audit_event_order = 1
          """,
          "audit_event");
      assertAppendOnlyViolation(
          requireStoreDatabase(postingFactStore),
          "delete from audit_event where audit_event_order = 1",
          "audit_event");
    }
  }

  private static void assertAppendOnlyViolation(
      SqliteNativeDatabase database, String sql, String relationName) {
    SqliteNativeException exception =
        assertThrows(SqliteNativeException.class, () -> database.executeStatement(sql));
    assertEquals(SqliteNativeResultCode.code("CONSTRAINT_TRIGGER"), exception.resultCode());
    assertFalse(NullTestSupport.messageOf(exception).isBlank(), () -> relationName + " message");
  }
}
