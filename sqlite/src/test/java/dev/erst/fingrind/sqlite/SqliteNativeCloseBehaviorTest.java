package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
@NullUnmarked
class SqliteNativeCloseBehaviorTest extends SqliteNativeBridgeTestSupport {

  @Test
  void close_rethrowsSqliteNativeExceptionFromNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("failThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "close native failure", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      SqliteNativeException exception = assertThrows(SqliteNativeException.class, database::close);

      assertEquals("SQLITE_CANTOPEN", exception.resultName());
    }
  }

  @Test
  void close_keepsActiveConnectionCountUntilSuccessfulRetry() throws Exception {
    Path bookPath = tempDirectory.resolve("close-active-count.sqlite");
    int initialActiveConnections = SqliteNativeBootstrap.activeConnectionCount();
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("failThenDelegateCloseCall", closeCalls);

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close active count", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());

      assertThrows(SqliteNativeException.class, database::close);
      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());

      assertEquals(initialActiveConnections + 1, SqliteNativeBootstrap.activeConnectionCount());
    }

    assertEquals(initialActiveConnections, SqliteNativeBootstrap.activeConnectionCount());
  }

  @Test
  void close_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-throwable.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi =
        closeBehaviorApi("throwIllegalStateThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close throwable", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, database::close);

      assertEquals("Failed to close the SQLite native library bridge.", exception.getMessage());
      assertEquals("boom", exception.getCause().getMessage());
    }
  }

  @Test
  void close_rethrowsErrorsFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-error.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    SqliteNativeApi sqliteApi = closeBehaviorApi("throwAssertionThenDelegateCloseCall", closeCalls);
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close error", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath, passphrase, SqliteNativeOpenMode.READ_WRITE_CREATE, sqliteApi)) {
      AssertionError error = assertThrows(AssertionError.class, database::close);

      assertEquals("boom", error.getMessage());
    }
  }

  private static SqliteNativeApi closeBehaviorApi(String helperMethodName, AtomicInteger closeCalls)
      throws ReflectiveOperationException {
    SqliteNativeApi baseApi = SqliteNativeBootstrap.api();
    SqliteNativeCalls.AddressToIntCall delegateClose =
        SqliteNativeCalls.addressToInt(baseApi.sqlite3CloseV2());
    return SqliteNativeApiTestSupport.withCloseV2(
        baseApi,
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findStatic(
                    SqliteNativeBridgeTestSupport.class,
                    helperMethodName,
                    java.lang.invoke.MethodType.methodType(
                        int.class,
                        AtomicInteger.class,
                        SqliteNativeCalls.AddressToIntCall.class,
                        MemorySegment.class)),
            0,
            closeCalls,
            delegateClose));
  }
}
