package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/** Verifies every terminal and recoverable transition of the in-memory SQLite session state. */
class SqliteStoreSessionStateTrackerTest extends SqliteStoreFixtureSupport {
  @Test
  void stateTransitions_preserveDatabaseCacheAndTerminalFailureInvariants() {
    try (SqliteSessionSecret sessionSecret =
            new SqliteSessionSecret(
                SqliteBookPassphrase.fromCharacters(
                    "session-state-tracker", TEST_BOOK_KEY.toCharArray()));
        SqliteNativeDatabase database = inertDatabase()) {
      SqliteBookStateSnapshot initial =
          new SqliteBookStateSnapshot(1, 25, SqliteBookState.INITIALIZED_FINGRIND);
      SqliteBookStateSnapshot replacement =
          new SqliteBookStateSnapshot(1, 26, SqliteBookState.INITIALIZED_FINGRIND);

      SqliteStoreSessionStateTracker idle = new SqliteStoreSessionStateTracker(sessionSecret);
      idle.ensureOpen();
      assertNull(idle.publishedDatabase());
      idle.cacheState(initial);
      assertEquals(initial, idle.cachedBookState());
      idle.clearCachedState();
      assertNull(idle.cachedBookState());
      idle.clearDatabaseState();
      assertNull(idle.detachPublishedDatabase());

      SqliteStoreSessionStateTracker opened = new SqliteStoreSessionStateTracker(sessionSecret);
      opened.cacheState(initial);
      opened.publishDatabase(database);
      assertSame(database, opened.publishedDatabase());
      opened.cacheState(replacement);
      assertEquals(replacement, opened.cachedBookState());
      opened.clearCachedState();
      assertNull(opened.cachedBookState());
      opened.publishDatabase(database);
      assertSame(database, opened.detachPublishedDatabase());
      assertNull(opened.publishedDatabase());

      SqliteStoreSessionStateTracker failed = new SqliteStoreSessionStateTracker(sessionSecret);
      failed.cacheState(initial);
      failed.publishDatabase(database);
      IllegalStateException initialFailure = new IllegalStateException("database open failed");
      assertSame(initialFailure, failed.rememberTerminalFailure(initialFailure));
      assertSame(initialFailure, assertThrows(IllegalStateException.class, failed::ensureOpen));
      failed.cacheState(replacement);
      assertEquals(replacement, failed.cachedBookState());
      failed.clearCachedState();
      assertNull(failed.cachedBookState());
      failed.publishDatabase(database);
      assertSame(database, failed.detachPublishedDatabase());
      failed.clearDatabaseState();
      assertSame(initialFailure, assertThrows(IllegalStateException.class, failed::ensureOpen));
      IllegalStateException replacementFailure = new IllegalStateException("replacement failure");
      assertSame(replacementFailure, failed.rememberTerminalFailure(replacementFailure));
      assertEquals(
          "Failed-session rejection.",
          failed
              .rememberedRejectedFailure(
                  ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                      "Failed-session rejection.", null, null))
              .failure()
              .message());

      ContractFailureException storedRejected =
          new ContractFailureException(
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                  "Stored rejection.", null, null));
      SqliteStoreSessionStateTracker rejected = new SqliteStoreSessionStateTracker(sessionSecret);
      rejected.rememberTerminalFailure(storedRejected);
      assertSame(
          storedRejected,
          rejected.rememberedRejectedFailure(
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                  "Different rejection.", null, null)));
      SqliteStoreSessionStateTracker unstoredRejected =
          new SqliteStoreSessionStateTracker(sessionSecret);
      ContractFailureException freshRejected =
          unstoredRejected.rememberedRejectedFailure(
              ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
                  "Fresh rejection.", null, null));
      assertEquals("Fresh rejection.", freshRejected.failure().message());

      SqliteStoreSessionStateTracker closed = new SqliteStoreSessionStateTracker(sessionSecret);
      closed.cacheState(initial);
      closed.publishDatabase(database);
      closed.markClosed(null);
      closed.cacheState(replacement);
      closed.clearCachedState();
      closed.clearDatabaseState();
      closed.publishDatabase(database);
      assertNull(closed.publishedDatabase());
      assertNull(closed.cachedBookState());
      assertNull(closed.detachPublishedDatabase());
      assertEquals(
          "SQLite book session is already closed.",
          assertThrows(IllegalStateException.class, closed::ensureOpen).getMessage());
      IllegalStateException closeFailure = new IllegalStateException("close failed");
      assertSame(closeFailure, closed.rememberTerminalFailure(closeFailure));
      assertSame(closeFailure, assertThrows(IllegalStateException.class, closed::ensureOpen));
    }
  }

  @Test
  void stateSnapshotsAndAccessModesRejectInvalidStateAtTheirExactBoundary() {
    assertEquals(
        "SQLite applicationId must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqliteBookStateSnapshot(-1, 0, SqliteBookState.BLANK_SQLITE))
            .getMessage());
    assertEquals(
        "SQLite userVersion must be non-negative.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqliteBookStateSnapshot(0, -1, SqliteBookState.BLANK_SQLITE))
            .getMessage());

    assertEquals(
        "This FinGrind SQLite session is read-only and cannot mutate the book.",
        assertThrows(
                IllegalStateException.class,
                SqliteStoreAccessMode.READ_ONLY::requireWritableMutation)
            .getMessage());
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableMutation);
    assertEquals(
        "This FinGrind SQLite session cannot initialize or create a book file.",
        assertThrows(
                IllegalStateException.class,
                SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableInitialization)
            .getMessage());
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableInitialization);
  }

  @Test
  void rollbackQuietly_preservesThePrimaryFailureWhenCleanupCannotReachSQLite() {
    try (SqliteNativeDatabase rollbackFailingDatabase =
        new SqliteNativeDatabase(MemorySegment.NULL) {
          @Override
          void executeStatement(String sql) {
            throw new IllegalStateException("rollback failure");
          }

          @Override
          public void close() {}
        }) {

      assertDoesNotThrow(() -> SqliteStoreOperations.rollbackQuietly(rollbackFailingDatabase));
    }
  }

  private static SqliteNativeDatabase inertDatabase() {
    return new SqliteNativeDatabase(MemorySegment.NULL) {
      @Override
      public void close() {}
    };
  }
}
