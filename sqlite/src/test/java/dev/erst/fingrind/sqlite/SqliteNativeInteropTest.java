package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.application.BookAccess;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for the low-level SQLite FFM bridge failure paths. */
class SqliteNativeInteropTest {
  private static final String TEST_BOOK_KEY = "interop-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void nullHandleCalls_mapToBridgeFailures() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlPointer = arena.allocateFrom("select 1");
      MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment textPointer = arena.allocateFrom("x");

      assertBridgeFailure(() -> SqliteNativeLibrary.close(null));
      assertBridgeFailure(() -> SqliteNativeLibrary.executeScript(null, sqlPointer));
      assertBridgeFailure(
          () -> SqliteNativeLibrary.prepareStatement(null, sqlPointer, statementPointer));
      assertBridgeFailure(() -> SqliteNativeLibrary.bindNull(null, 1));
      assertBridgeFailure(() -> SqliteNativeLibrary.bindInt(null, 1, 7));
      assertBridgeFailure(() -> SqliteNativeLibrary.bindText(null, 1, textPointer, 1));
      assertBridgeFailure(() -> SqliteNativeLibrary.step(null, null));
      assertBridgeFailure(() -> SqliteNativeLibrary.finalizeStatement(null));
      assertBridgeFailure(() -> SqliteNativeLibrary.columnText(null, 0));
      assertBridgeFailure(() -> SqliteNativeLibrary.columnInt(null, 0));
      assertBridgeFailure(() -> SqliteNativeLibrary.extendedErrorCode(null));
    }
  }

  @Test
  void invalidSqlAndConstraintFailures_mapToSQLiteFailures() throws Exception {
    SqliteNativeDatabase database =
        SqliteNativeLibrary.open(bookAccess(tempDirectory.resolve("interop.sqlite")));
    try {
      database.executeStatement("create table sample (id integer primary key)");

      try (Arena arena = Arena.ofConfined()) {
        MemorySegment sqlPointer = arena.allocateFrom("select from");
        MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteNativeLibrary.prepareStatement(
                    database.handle(), sqlPointer, statementPointer));
      }

      try (SqliteNativeStatement statement =
          SqliteNativeLibrary.prepare(database, "insert into sample (id) values (?)")) {
        MemorySegment statementHandle = statementHandle(statement);

        try (Arena arena = Arena.ofConfined()) {
          MemorySegment textPointer = arena.allocateFrom("x");
          assertThrows(
              SqliteNativeException.class, () -> SqliteNativeLibrary.bindNull(statementHandle, 0));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeLibrary.bindInt(statementHandle, 0, 7));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeLibrary.bindText(statementHandle, 0, textPointer, 1));
        }
      }

      assertThrows(
          SqliteNativeException.class, () -> new SqliteNativeStatement(database, "select from"));
      assertThrows(NullPointerException.class, () -> new SqliteNativeStatement(database, null));

      database.executeStatement("insert into sample (id) values (1)");
      try (SqliteNativeStatement duplicateInsert =
          SqliteNativeLibrary.prepare(database, "insert into sample (id) values (1)")) {
        SqliteNativeException exception =
            assertThrows(
                SqliteNativeException.class,
                () ->
                    SqliteNativeLibrary.step(database.handle(), statementHandle(duplicateInsert)));
        assertEquals(
            SqliteNativeLibrary.SQLITE_CONSTRAINT_PRIMARYKEY,
            SqliteNativeLibrary.extendedErrorCode(database.handle()));
        assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", exception.resultName());
      }
    } finally {
      database.close();
    }
  }

  @Test
  void executeScript_surfacesTypedSqliteFailureForInvalidSql() throws Exception {
    SqliteNativeDatabase database =
        SqliteNativeLibrary.open(bookAccess(tempDirectory.resolve("script-failure.sqlite")));
    try {
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  database.executeScript(
                      """
                      create table sample (id integer primary key);
                      create table broken (
                      """));

      assertEquals(1, exception.resultCode());
      assertEquals("SQLITE_1", exception.resultName());
    } finally {
      database.close();
    }
  }

  @Test
  void executeStatement_rejectsRowProducingSql() throws Exception {
    SqliteNativeDatabase database =
        SqliteNativeLibrary.open(bookAccess(tempDirectory.resolve("row-producing.sqlite")));
    try {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> database.executeStatement("select 1"));

      assertEquals(
          "SQLite control statement must not produce rows: select 1", exception.getMessage());
    } finally {
      database.close();
    }
  }

  @Test
  void mapper_readsReversalReferenceOnlyFromPriorPostingIdColumn() throws Exception {
    SqliteNativeDatabase database =
        SqliteNativeLibrary.open(bookAccess(tempDirectory.resolve("mapper.sqlite")));
    try {
      try (SqliteNativeStatement missingPrior =
          SqliteNativeLibrary.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, null
              """)) {
        assertEquals(SqliteNativeLibrary.SQLITE_ROW, missingPrior.step());
        assertEquals(
            java.util.Optional.empty(), SqlitePostingMapper.readReversalReference(missingPrior));
      }

      try (SqliteNativeStatement presentPriorPostingId =
          SqliteNativeLibrary.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, 'posting-1'
              """)) {
        assertEquals(SqliteNativeLibrary.SQLITE_ROW, presentPriorPostingId.step());
        assertEquals(
            java.util.Optional.of(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1"))),
            SqlitePostingMapper.readReversalReference(presentPriorPostingId));
      }
    } finally {
      database.close();
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void databaseAndStatementClose_areIdempotent() throws Exception {
    SqliteNativeDatabase database =
        SqliteNativeLibrary.open(bookAccess(tempDirectory.resolve("close.sqlite")));
    try {
      SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, "select 1");
      assertDoesNotThrow(statement::close);
      assertDoesNotThrow(statement::close);
    } finally {
      assertDoesNotThrow(database::close);
      assertDoesNotThrow(database::close);
    }
  }

  @Test
  void helperOverloads_coverBridgeFailures() throws Throwable {
    MethodHandle throwingVersionHandle =
        MethodHandles.throwException(MemorySegment.class, IllegalStateException.class)
            .bindTo(new IllegalStateException("boom"));
    MethodHandle returningVersionHandle =
        MethodHandles.constant(MemorySegment.class, MemorySegment.NULL);
    MethodHandle throwingErrorHandle =
        MethodHandles.dropArguments(throwingVersionHandle, 0, MemorySegment.class);
    MethodHandle nullErrorHandle =
        MethodHandles.dropArguments(
            MethodHandles.constant(MemorySegment.class, MemorySegment.NULL),
            0,
            MemorySegment.class);
    MethodHandle throwingStrlenHandle =
        MethodHandles.throwException(long.class, IllegalStateException.class)
            .bindTo(new IllegalStateException("boom"));

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MemorySegment messagePointer = arena.allocateFrom("boom");
      MethodHandle messageHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, messagePointer), 0, MemorySegment.class);

      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeLibrary.sqliteVersion(throwingVersionHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeLibrary.sqliteVersion(returningVersionHandle, throwingStrlenHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeLibrary.errorMessage(fakeHandle, throwingErrorHandle));
      assertEquals(
          "SQLite native failure.", SqliteNativeLibrary.errorMessage(fakeHandle, nullErrorHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeLibrary.errorMessage(fakeHandle, messageHandle, throwingStrlenHandle));
    }
  }

  @Test
  void errorMessage_readsWholeCStringWithoutFixedTruncation() throws Throwable {
    String longMessage = "x".repeat(5_000);
    MethodHandle longMessageHandle;

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MemorySegment messagePointer = arena.allocateFrom(longMessage);
      longMessageHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, messagePointer), 0, MemorySegment.class);

      assertEquals(longMessage, SqliteNativeLibrary.errorMessage(fakeHandle, longMessageHandle));
    }
  }

  @Test
  void scriptErrorMessage_prefersExecOwnedErrorBufferWhenPresent() throws Throwable {
    MethodHandle throwingErrorHandle =
        MethodHandles.dropArguments(
            MethodHandles.throwException(MemorySegment.class, IllegalStateException.class)
                .bindTo(new IllegalStateException("fallback should not run")),
            0,
            MemorySegment.class);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MemorySegment execErrorPointer = arena.allocateFrom("exec-owned failure");

      assertEquals(
          "exec-owned failure",
          SqliteNativeLibrary.scriptErrorMessage(
              fakeHandle, execErrorPointer, throwingErrorHandle, strlenHandle()));
    }
  }

  @Test
  void scriptErrorMessage_fallsBackToDatabaseErrorWhenExecBufferIsMissing() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MemorySegment databaseErrorPointer = arena.allocateFrom("database failure");
      MethodHandle databaseErrorHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, databaseErrorPointer),
              0,
              MemorySegment.class);

      assertEquals(
          "database failure",
          SqliteNativeLibrary.scriptErrorMessage(
              fakeHandle, MemorySegment.NULL, databaseErrorHandle, strlenHandle()));
    }
  }

  @Test
  void scriptErrorMessage_fallsBackToDatabaseErrorWhenExecBufferReferenceIsNull() throws Throwable {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment fakeHandle = arena.allocate(1);
      MemorySegment databaseErrorPointer = arena.allocateFrom("database failure");
      MethodHandle databaseErrorHandle =
          MethodHandles.dropArguments(
              MethodHandles.constant(MemorySegment.class, databaseErrorPointer),
              0,
              MemorySegment.class);

      assertEquals(
          "database failure",
          SqliteNativeLibrary.scriptErrorMessage(
              fakeHandle, null, databaseErrorHandle, strlenHandle()));
    }
  }

  @Test
  void freeSqliteBuffer_wrapsBridgeFailureForNonNullPointers() throws Throwable {
    MethodHandle throwingFreeHandle =
        MethodHandles.dropArguments(
            MethodHandles.throwException(void.class, IllegalStateException.class)
                .bindTo(new IllegalStateException("boom")),
            0,
            MemorySegment.class);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pointer = arena.allocate(1);

      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeLibrary.freeSqliteBuffer(pointer, throwingFreeHandle));
    }
  }

  private static void assertBridgeFailure(ThrowingRunnable runnable) {
    assertThrows(IllegalStateException.class, runnable::run);
  }

  private static BookAccess bookAccess(Path bookPath) {
    try {
      Path keyPath = bookPath.resolveSibling(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      Files.writeString(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, keyPath);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static MethodHandle strlenHandle() throws NoSuchMethodException, IllegalAccessException {
    return MethodHandles.lookup()
        .findStatic(
            SqliteNativeInteropTest.class,
            "strlen",
            java.lang.invoke.MethodType.methodType(long.class, MemorySegment.class));
  }

  @SuppressWarnings("unused")
  private static long strlen(MemorySegment pointer) {
    return pointer.getString(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static MemorySegment statementHandle(SqliteNativeStatement statement) throws Exception {
    Field field = SqliteNativeStatement.class.getDeclaredField("statementHandle");
    field.setAccessible(true);
    return (MemorySegment) field.get(statement);
  }

  /** Runnable that can throw checked exceptions while exercising low-level bridge failures. */
  @FunctionalInterface
  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
