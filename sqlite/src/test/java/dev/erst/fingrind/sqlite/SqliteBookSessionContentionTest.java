package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Deterministic same-book contention coverage for the public SQLite session surface. */
class SqliteBookSessionContentionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void secondWriterHonorsBusyTimeoutWhenFirstWriterHoldsImmediateTransaction() {
    Path bookPath = tempDirectory.resolve("contention-book.sqlite");
    try (SqliteNativeDatabase firstWriter = SqliteNativeConnections.open(bookAccess(bookPath));
        SqliteNativeDatabase secondWriter = SqliteNativeConnections.open(bookAccess(bookPath))) {
      firstWriter.executeStatement("begin immediate");
      setBusyTimeout(secondWriter, 100);
      long startNanos = System.nanoTime();
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class, () -> secondWriter.executeStatement("begin immediate"));
      long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
      assertTrue(
          Set.of(
                  SqliteNativeResultCodes.BUSY,
                  SqliteNativeResultCodes.BUSY_TIMEOUT,
                  SqliteNativeResultCodes.LOCKED)
              .contains(exception.resultCode()),
          () -> "Unexpected SQLite contention code: " + exception.resultName());
      assertTrue(
          elapsedMillis >= 50 && elapsedMillis < 2_500,
          () -> "Expected a shortened busy-timeout failure, but elapsedMillis=" + elapsedMillis);
      assertTrue(
          exception.resultName().startsWith("SQLITE_BUSY")
              || exception.resultName().startsWith("SQLITE_LOCKED"),
          () -> "Unexpected SQLite contention name: " + exception.resultName());
      firstWriter.executeStatement("rollback");
    }
  }

  private static void setBusyTimeout(SqliteNativeDatabase database, int timeoutMillis) {
    int resultCode =
        SqliteNativeCalls.addressIntToInt(database.sqliteApi().sqlite3BusyTimeout())
            .invoke(database.handle(), timeoutMillis);
    SqliteNativeConnections.requireOpenConfigurationSuccess(resultCode, database.sqliteApi());
  }
}
