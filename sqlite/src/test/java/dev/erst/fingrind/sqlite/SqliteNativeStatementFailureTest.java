package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct proof for statement-scoped failure ownership and cleanup behavior. */
class SqliteNativeStatementFailureTest extends SqliteNativeBridgeTestSupport {
  private static final int API_PREPARE_V2 = 14;
  private static final int API_BIND_INT = 16;
  private static final int API_BIND_INT64 = 17;
  private static final int API_BIND_TEXT = 18;
  private static final int API_STEP = 19;
  private static final int API_FINALIZE = 20;
  private static final int API_COLUMN_TEXT = 21;
  private static final int API_COLUMN_INT = 23;
  private static final int API_COLUMN_INT64 = 24;

  @Test
  void bindAndReadOperations_recordFailuresBeforeRethrowing() {
    assertStatementFailure(
        API_BIND_TEXT,
        throwingMethodHandle(
            new IllegalStateException("bind text boom"),
            int.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class),
        statement -> statement.bindText(1, "boom"),
        IllegalStateException.class,
        "Failed to bind a SQLite text parameter.");
    assertStatementFailure(
        API_BIND_INT,
        throwingMethodHandle(
            new IllegalStateException("bind int boom"),
            int.class,
            MemorySegment.class,
            int.class,
            int.class),
        statement -> statement.bindInt(1, 7),
        IllegalStateException.class,
        "Failed to bind a SQLite integer parameter.");
    assertStatementFailure(
        API_BIND_INT64,
        throwingMethodHandle(
            new IllegalStateException("bind long boom"),
            int.class,
            MemorySegment.class,
            int.class,
            long.class),
        statement -> statement.bindLong(1, 7L),
        IllegalStateException.class,
        "Failed to bind a SQLite integer parameter.");
    assertStatementFailure(
        API_STEP,
        throwingMethodHandle(
            new IllegalStateException("step boom"), int.class, MemorySegment.class),
        statement -> statement.step(),
        IllegalStateException.class,
        "Failed to step a SQLite statement.");
    assertStatementFailure(
        API_COLUMN_TEXT,
        throwingMethodHandle(
            new IllegalStateException("column text boom"),
            MemorySegment.class,
            MemorySegment.class,
            int.class),
        statement -> statement.columnText(0),
        IllegalStateException.class,
        "Failed to read a SQLite text column.");
    assertStatementFailure(
        API_COLUMN_INT,
        throwingMethodHandle(
            new IllegalStateException("column int boom"),
            int.class,
            MemorySegment.class,
            int.class),
        statement -> statement.columnInt(0),
        IllegalStateException.class,
        "Failed to read a SQLite integer column.");
    assertStatementFailure(
        API_COLUMN_INT64,
        throwingMethodHandle(
            new IllegalStateException("column long boom"),
            long.class,
            MemorySegment.class,
            int.class),
        statement -> statement.columnLong(0),
        IllegalStateException.class,
        "Failed to read a SQLite integer column.");
  }

  @Test
  void close_rethrowsRuntimeFailuresFromFinalizeWhenNoPriorFailureExists() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(
                    arena.allocate(1),
                    statementApi(
                        API_FINALIZE,
                        throwingMethodHandle(
                            new IllegalStateException("finalize boom"),
                            int.class,
                            MemorySegment.class))),
                "select 1")) {

      IllegalStateException exception = assertThrows(IllegalStateException.class, statement::close);

      assertEquals("Failed to finalize a SQLite statement.", exception.getMessage());
      assertEquals("finalize boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  @Test
  void close_rethrowsErrorsFromFinalizeWhenNoPriorFailureExists() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(
                    arena.allocate(1),
                    statementApi(
                        API_FINALIZE,
                        throwingMethodHandle(
                            new AssertionError("finalize boom"), int.class, MemorySegment.class))),
                "select 1")) {

      AssertionError error = assertThrows(AssertionError.class, statement::close);

      assertEquals("finalize boom", error.getMessage());
    }
  }

  @Test
  void close_suppressesFinalizeRuntimeFailuresAfterRecordedStatementFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(
                    arena.allocate(1),
                    statementApi(
                        API_BIND_INT,
                        throwingMethodHandle(
                            new IllegalStateException("bind int boom"),
                            int.class,
                            MemorySegment.class,
                            int.class,
                            int.class),
                        throwingMethodHandle(
                            new IllegalStateException("finalize boom"),
                            int.class,
                            MemorySegment.class))),
                "select 1")) {

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> statement.bindInt(1, 7));
      assertDoesNotThrow(statement::close);
      assertEquals(1, exception.getSuppressed().length);
      assertEquals(
          "Failed to finalize a SQLite statement.", exception.getSuppressed()[0].getMessage());
      assertEquals(
          "finalize boom",
          NullTestSupport.messageOf(NullTestSupport.causeOf(exception.getSuppressed()[0])));
    }
  }

  @Test
  void close_suppressesFinalizeErrorsAfterRecordedStatementFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(
                    arena.allocate(1),
                    statementApi(
                        API_BIND_INT,
                        throwingMethodHandle(
                            new IllegalStateException("bind int boom"),
                            int.class,
                            MemorySegment.class,
                            int.class,
                            int.class),
                        throwingMethodHandle(
                            new AssertionError("finalize boom"), int.class, MemorySegment.class))),
                "select 1")) {

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> statement.bindInt(1, 7));
      assertDoesNotThrow(statement::close);
      assertEquals(1, exception.getSuppressed().length);
      assertEquals("finalize boom", exception.getSuppressed()[0].getMessage());
    }
  }

  @Test
  void secondFailureDoesNotReplaceTheFirstRecordedStatementFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(
                    arena.allocate(1),
                    statementApiForTwoFailures(
                        throwingMethodHandle(
                            new IllegalStateException("bind int boom"),
                            int.class,
                            MemorySegment.class,
                            int.class,
                            int.class),
                        throwingMethodHandle(
                            new IllegalStateException("step boom"), int.class, MemorySegment.class),
                        throwingMethodHandle(
                            new IllegalStateException("finalize boom"),
                            int.class,
                            MemorySegment.class))),
                "select 1")) {

      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, () -> statement.bindInt(1, 7));
      IllegalStateException secondFailure =
          assertThrows(IllegalStateException.class, () -> statement.step());

      assertDoesNotThrow(statement::close);
      assertEquals(1, firstFailure.getSuppressed().length);
      assertEquals(
          "Failed to finalize a SQLite statement.", firstFailure.getSuppressed()[0].getMessage());
      assertEquals(0, secondFailure.getSuppressed().length);
    }
  }

  private static <T extends Throwable> void assertStatementFailure(
      int apiIndex,
      MethodHandle failingHandle,
      StatementAction action,
      Class<T> failureType,
      String expectedMessage) {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeStatement statement =
            new SqliteNativeStatement(
                new SqliteNativeDatabase(arena.allocate(1), statementApi(apiIndex, failingHandle)),
                "select 1")) {

      T exception = assertThrows(failureType, () -> action.run(statement));

      assertEquals(expectedMessage, exception.getMessage());
      assertDoesNotThrow(statement::close);
    }
  }

  private static SqliteNativeApi statementApi(int apiIndex, MethodHandle handle) {
    return statementApi(apiIndex, handle, null);
  }

  private static SqliteNativeApi statementApi(
      int apiIndex, MethodHandle handle, @Nullable MethodHandle finalizeHandle) {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[API_PREPARE_V2] =
        constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[apiIndex] = handle;
    if (finalizeHandle != null) {
      sqliteApiArguments[API_FINALIZE] = finalizeHandle;
    }
    return buildSqliteApi(sqliteApiArguments);
  }

  private static SqliteNativeApi statementApiForTwoFailures(
      MethodHandle bindIntHandle, MethodHandle stepHandle, MethodHandle finalizeHandle) {
    Object[] sqliteApiArguments = defaultSqliteApiArguments();
    sqliteApiArguments[API_PREPARE_V2] =
        constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[API_BIND_INT] = bindIntHandle;
    sqliteApiArguments[API_STEP] = stepHandle;
    sqliteApiArguments[API_FINALIZE] = finalizeHandle;
    return buildSqliteApi(sqliteApiArguments);
  }

  /** Test seam for one statement action whose failure ownership should be asserted directly. */
  @FunctionalInterface
  private interface StatementAction {
    /** Runs one statement action that may record and rethrow a statement-scoped failure. */
    void run(SqliteNativeStatement statement);
  }
}
