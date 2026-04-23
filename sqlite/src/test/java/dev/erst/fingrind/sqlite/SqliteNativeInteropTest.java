package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.PostingLineage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for the low-level SQLite FFM bridge failure paths. */
@NullUnmarked
class SqliteNativeInteropTest {
  private static final String TEST_BOOK_KEY = "interop-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void nullHandleCalls_mapToBridgeFailures() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlPointer = arena.allocateFrom("select 1");
      MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment textPointer = arena.allocateFrom("x");

      assertBridgeFailure(() -> SqliteNativeConnections.close(null, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.executeScript(null, sqlPointer, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () -> SqliteNativeStatements.prepareStatement(null, sqlPointer, statementPointer));
      assertBridgeFailure(() -> SqliteNativeStatements.bindNull(null, 1));
      assertBridgeFailure(() -> SqliteNativeStatements.bindInt(null, 1, 7));
      assertBridgeFailure(() -> SqliteNativeStatements.bindText(null, 1, textPointer, 1));
      assertBridgeFailure(() -> SqliteNativeStatements.step(null, null));
      assertBridgeFailure(() -> SqliteNativeStatements.finalizeStatement(null));
      assertBridgeFailure(() -> SqliteNativeStatements.columnText(null, 0));
      assertBridgeFailure(() -> SqliteNativeStatements.columnInt(null, 0));
      assertBridgeFailure(() -> SqliteNativeStatements.extendedErrorCode(null));
    }
  }

  @Test
  void invalidSqlAndConstraintFailures_mapToSQLiteFailures() throws Exception {
    try (SqliteNativeDatabase database =
        SqliteNativeConnections.open(bookAccess(tempDirectory.resolve("interop.sqlite")))) {
      database.executeStatement("create table sample (id integer primary key)");

      try (Arena arena = Arena.ofConfined()) {
        MemorySegment sqlPointer = arena.allocateFrom("select from");
        MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteNativeStatements.prepareStatement(
                    database.handle(), sqlPointer, statementPointer));
      }

      try (SqliteNativeStatement statement =
          SqliteNativeStatements.prepare(database, "insert into sample (id) values (?)")) {
        MemorySegment statementHandle = statement.handle();

        try (Arena arena = Arena.ofConfined()) {
          MemorySegment textPointer = arena.allocateFrom("x");
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindNull(statementHandle, 0));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindInt(statementHandle, 0, 7));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindText(statementHandle, 0, textPointer, 1));
        }
      }

      assertThrows(
          SqliteNativeException.class, () -> new SqliteNativeStatement(database, "select from"));
      assertThrows(NullPointerException.class, () -> new SqliteNativeStatement(database, null));

      database.executeStatement("insert into sample (id) values (1)");
      try (SqliteNativeStatement duplicateInsert =
          SqliteNativeStatements.prepare(database, "insert into sample (id) values (1)")) {
        SqliteNativeException exception =
            assertThrows(
                SqliteNativeException.class,
                () -> SqliteNativeStatements.step(database.handle(), duplicateInsert.handle()));
        assertEquals(
            SqliteNativeResultCodes.CONSTRAINT_PRIMARYKEY,
            SqliteNativeStatements.extendedErrorCode(database.handle()));
        assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", exception.resultName());
      }
    }
  }

  @Test
  void executeScript_surfacesTypedSqliteFailureForInvalidSql() throws Exception {
    try (SqliteNativeDatabase database =
        SqliteNativeConnections.open(bookAccess(tempDirectory.resolve("script-failure.sqlite")))) {
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
    }
  }

  @Test
  void executeStatement_rejectsRowProducingSql() throws Exception {
    try (SqliteNativeDatabase database =
        SqliteNativeConnections.open(bookAccess(tempDirectory.resolve("row-producing.sqlite")))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> database.executeStatement("select 1"));

      assertEquals(
          "SQLite control statement must not produce rows: select 1", exception.getMessage());
    }
  }

  @Test
  void mapper_readsPostingLineageOnlyFromCoupledPriorPostingIdAndReasonColumns() throws Exception {
    try (SqliteNativeDatabase database =
        SqliteNativeConnections.open(bookAccess(tempDirectory.resolve("mapper.sqlite")))) {
      try (SqliteNativeStatement missingPrior =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, null
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, missingPrior.step());
        assertEquals(PostingLineage.direct(), SqlitePostingMapper.readPostingLineage(missingPrior));
      }

      try (SqliteNativeStatement missingPriorForWrapper =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, null
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, missingPriorForWrapper.step());
        assertEquals(
            java.util.Optional.empty(),
            SqlitePostingMapper.readReversalReference(missingPriorForWrapper));
      }

      try (SqliteNativeStatement presentPriorPostingId =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, 'operator reversal', null, 'posting-1'
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, presentPriorPostingId.step());
        assertEquals(
            PostingLineage.reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")),
            SqlitePostingMapper.readPostingLineage(presentPriorPostingId));
      }

      try (SqliteNativeStatement missingReason =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, 'posting-1'
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, missingReason.step());
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqlitePostingMapper.readPostingLineage(missingReason));
        assertEquals(
            "Persisted posting lineage is inconsistent: reversal reference and reason must be present together.",
            exception.getMessage());
      }

      try (SqliteNativeStatement missingPriorPostingId =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, 'operator reversal', null, null
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, missingPriorPostingId.step());
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqlitePostingMapper.readPostingLineage(missingPriorPostingId));
        assertEquals(
            "Persisted posting lineage is inconsistent: reversal reference and reason must be present together.",
            exception.getMessage());
      }
    }
  }

  @Test
  void databaseAndStatementClose_areIdempotent() throws Exception {
    try (SqliteNativeDatabase database =
        SqliteNativeConnections.open(bookAccess(tempDirectory.resolve("close.sqlite")))) {
      try (SqliteNativeStatement statement = SqliteNativeStatements.prepare(database, "select 1")) {
        assertDoesNotThrow(statement::close);
        assertDoesNotThrow(statement::close);
      }
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

      assertEquals(4L, strlen(messagePointer));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeBootstrap.sqliteVersion(throwingVersionHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeBootstrap.sqliteVersion(returningVersionHandle, throwingStrlenHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeErrors.errorMessage(fakeHandle, throwingErrorHandle));
      assertEquals(
          "SQLite native failure.", SqliteNativeErrors.errorMessage(fakeHandle, nullErrorHandle));
      assertThrows(
          IllegalStateException.class,
          () -> SqliteNativeErrors.errorMessage(fakeHandle, messageHandle, throwingStrlenHandle));
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

      assertEquals(longMessage, SqliteNativeErrors.errorMessage(fakeHandle, longMessageHandle));
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
          SqliteNativeErrors.scriptErrorMessage(
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
          SqliteNativeErrors.scriptErrorMessage(
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
          SqliteNativeErrors.scriptErrorMessage(
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
          () -> SqliteNativeErrors.freeSqliteBuffer(pointer, throwingFreeHandle));
    }
  }

  private static void assertBridgeFailure(Executable runnable) {
    assertThrows(IllegalStateException.class, runnable);
  }

  private static BookAccess bookAccess(Path bookPath) {
    try {
      Path keyPath = bookPath.resolveSibling(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      if (Files.notExists(keyPath)) {
        SqliteBookKeyFileGenerator.generate(keyPath);
      } else {
        SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);
      }
      Files.writeString(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
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

  private static long strlen(MemorySegment pointer) {
    return pointer.getString(0).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }
}
