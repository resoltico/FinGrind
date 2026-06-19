package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Direct unit coverage for retry and failure helpers in {@link SqliteStoreOperations}. */
class SqliteStoreOperationsTest {
  @Test
  void retryTransientLockFailures_returnsAfterOneTransientRetry() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        SqliteStoreOperations.retryTransientLockFailures(
            () -> {
              if (attempts.getAndIncrement() == 0) {
                throw transientLockFailure();
              }
              return "accepted";
            });

    assertEquals("accepted", result);
    assertEquals(2, attempts.get());
  }

  @Test
  void retryTransientLockFailures_rethrowsNonTransientFailureWithoutRetry() {
    AtomicInteger attempts = new AtomicInteger();

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteStoreOperations.retryTransientLockFailures(
                    () -> {
                      attempts.incrementAndGet();
                      throw nonTransientFailure();
                    }));

    assertEquals("SQLITE_CANTOPEN", exception.resultName());
    assertEquals(1, attempts.get());
  }

  @Test
  void retryTransientLockFailures_rethrowsTransientFailureAfterRetryBudgetExhausted() {
    AtomicInteger attempts = new AtomicInteger();

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteStoreOperations.retryTransientLockFailures(
                    () -> {
                      attempts.incrementAndGet();
                      throw transientLockFailure();
                    }));

    assertEquals("SQLITE_BUSY", exception.resultName());
    assertEquals(8, attempts.get());
  }

  @Test
  void retryTransientLockFailures_wrapsInterruptedRetryPause() {
    AtomicInteger attempts = new AtomicInteger();

    Thread.currentThread().interrupt();
    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteStoreOperations.retryTransientLockFailures(
                      () -> {
                        attempts.incrementAndGet();
                        throw transientLockFailure();
                      }));

      assertEquals(
          "Interrupted while retrying one transient SQLite lock failure.", exception.getMessage());
      assertInstanceOf(InterruptedException.class, exception.getCause());
      assertEquals(1, attempts.get());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void sqliteFailure_wrapsNativeMessageAndCause() {
    SqliteNativeException cause = nonTransientFailure();

    SqliteStorageFailureException exception =
        assertInstanceOf(
            SqliteStorageFailureException.class,
            SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", cause));

    assertEquals(
        "Failed to query SQLite book. SQLITE_CANTOPEN: unable to open file",
        exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void sqliteFailure_deduplicatesRepeatedNativeResultNamePrefixes() {
    SqliteNativeException cause =
        new SqliteNativeException(
            SqliteNativeResultCode.code("CANTOPEN"), "SQLITE_CANTOPEN: unable to open file");

    SqliteStorageFailureException exception =
        assertInstanceOf(
            SqliteStorageFailureException.class,
            SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", cause));

    assertEquals(
        "Failed to query SQLite book. SQLITE_CANTOPEN: unable to open file",
        exception.getMessage());
  }

  @Test
  void sqliteFailure_usesFallbackNativeDetailWhenPrefixConsumesWholeMessage() {
    SqliteNativeException cause =
        new SqliteNativeException(SqliteNativeResultCode.code("CANTOPEN"), "SQLITE_CANTOPEN:");

    SqliteStorageFailureException exception =
        assertInstanceOf(
            SqliteStorageFailureException.class,
            SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", cause));

    assertEquals(
        "Failed to query SQLite book. SQLITE_CANTOPEN: SQLite native failure.",
        exception.getMessage());
  }

  @Test
  void sqliteFailure_wrapsConstraintCheckAsPersistenceInvariant() {
    SqliteNativeException cause =
        new SqliteNativeException(
            SqliteNativeResultCode.code("CONSTRAINT_CHECK"),
            "SQLITE_CONSTRAINT_CHECK: constraint failed");

    SqlitePersistenceInvariantException exception =
        assertInstanceOf(
            SqlitePersistenceInvariantException.class,
            SqliteStoreOperations.sqliteFailure("Failed to commit SQLite posting fact.", cause));

    assertEquals(
        "Failed to commit SQLite posting fact. One upstream invariant should have rejected this request before commit.",
        exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  private static SqliteNativeException transientLockFailure() {
    return new SqliteNativeException(SqliteNativeResultCode.code("BUSY"), "database is locked");
  }

  private static SqliteNativeException nonTransientFailure() {
    return new SqliteNativeException(
        SqliteNativeResultCode.code("CANTOPEN"), "unable to open file");
  }
}
