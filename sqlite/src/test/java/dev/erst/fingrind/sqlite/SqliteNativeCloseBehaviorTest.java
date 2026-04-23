package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "close native failure", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withCloseV2(
                    SqliteNativeBootstrap.api(),
                    MethodHandles.insertArguments(
                        MethodHandles.lookup()
                            .findStatic(
                                SqliteNativeBridgeTestSupport.class,
                                "failThenSucceedCloseCall",
                                java.lang.invoke.MethodType.methodType(
                                    int.class, AtomicInteger.class, MemorySegment.class)),
                        0,
                        closeCalls)))) {
      SqliteNativeException exception = assertThrows(SqliteNativeException.class, database::close);

      assertEquals("SQLITE_CANTOPEN", exception.resultName());
    }
  }

  @Test
  void close_keepsActiveConnectionCountUntilSuccessfulRetry() throws Exception {
    Path bookPath = tempDirectory.resolve("close-active-count.sqlite");
    int initialActiveConnections = SqliteNativeBootstrap.activeConnectionCount();
    AtomicInteger closeCalls = new AtomicInteger();

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close active count", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withCloseV2(
                    SqliteNativeBootstrap.api(),
                    MethodHandles.insertArguments(
                        MethodHandles.lookup()
                            .findStatic(
                                SqliteNativeBridgeTestSupport.class,
                                "failThenSucceedCloseCall",
                                java.lang.invoke.MethodType.methodType(
                                    int.class, AtomicInteger.class, MemorySegment.class)),
                        0,
                        closeCalls)))) {
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
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close throwable", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withCloseV2(
                    SqliteNativeBootstrap.api(),
                    MethodHandles.insertArguments(
                        MethodHandles.lookup()
                            .findStatic(
                                SqliteNativeBridgeTestSupport.class,
                                "throwIllegalStateThenSucceedCloseCall",
                                java.lang.invoke.MethodType.methodType(
                                    int.class, AtomicInteger.class, MemorySegment.class)),
                        0,
                        closeCalls)))) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, database::close);

      assertEquals("Failed to close the SQLite native library bridge.", exception.getMessage());
      assertEquals("boom", exception.getCause().getMessage());
    }
  }

  @Test
  void close_rethrowsErrorsFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("close-error.sqlite");
    AtomicInteger closeCalls = new AtomicInteger();
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("close error", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withCloseV2(
                    SqliteNativeBootstrap.api(),
                    MethodHandles.insertArguments(
                        MethodHandles.lookup()
                            .findStatic(
                                SqliteNativeBridgeTestSupport.class,
                                "throwAssertionThenSucceedCloseCall",
                                java.lang.invoke.MethodType.methodType(
                                    int.class, AtomicInteger.class, MemorySegment.class)),
                        0,
                        closeCalls)))) {
      AssertionError error = assertThrows(AssertionError.class, database::close);

      assertEquals("boom", error.getMessage());
    }
  }
}
