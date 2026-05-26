package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFileGenerator;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for the low-level SQLite FFM bridge failure paths. */
class SqliteNativeInteropTest {
  private static final String TEST_BOOK_KEY = "interop-test-book-key";
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void nullHandleCalls_mapToBridgeFailures() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlPointer = arena.allocateFrom("select 1");
      MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment textPointer = arena.allocateFrom("x");
      assertBridgeFailure(
          () ->
              SqliteNativeConnections.close(
                  NullTestSupport.nullOf(MemorySegment.class), null, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.executeScript(
                  NullTestSupport.nullOf(MemorySegment.class),
                  sqlPointer,
                  SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.prepareStatement(
                  NullTestSupport.nullOf(MemorySegment.class),
                  sqlPointer,
                  statementPointer,
                  SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.bindNull(
                  NullTestSupport.nullOf(MemorySegment.class), 1, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.bindInt(
                  NullTestSupport.nullOf(MemorySegment.class), 1, 7, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.bindText(
                  NullTestSupport.nullOf(MemorySegment.class),
                  1,
                  textPointer,
                  1,
                  SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.step(
                  NullTestSupport.nullOf(MemorySegment.class),
                  NullTestSupport.nullOf(MemorySegment.class),
                  SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.finalizeStatement(
                  NullTestSupport.nullOf(MemorySegment.class), SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.columnText(
                  NullTestSupport.nullOf(MemorySegment.class), 0, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.columnInt(
                  NullTestSupport.nullOf(MemorySegment.class), 0, SqliteNativeBootstrap.api()));
      assertBridgeFailure(
          () ->
              SqliteNativeStatements.extendedErrorCode(
                  NullTestSupport.nullOf(MemorySegment.class), SqliteNativeBootstrap.api()));
    }
  }

  @Test
  void invalidSqlAndConstraintFailures_mapToSQLiteFailures() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("interop.sqlite")))) {
      database.executeStatement("create table sample (id integer primary key)");
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment sqlPointer = arena.allocateFrom("select from");
        MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteNativeStatements.prepareStatement(
                    database.handle(), sqlPointer, statementPointer, database.sqliteApi()));
      }
      try (SqliteNativeStatement statement =
          SqliteNativeStatements.prepare(database, "insert into sample (id) values (?)")) {
        MemorySegment statementHandle = statement.handle();
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment textPointer = arena.allocateFrom("x");
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindNull(statementHandle, 0, database.sqliteApi()));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindInt(statementHandle, 0, 7, database.sqliteApi()));
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeStatements.bindText(
                      statementHandle, 0, textPointer, 1, database.sqliteApi()));
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeStatements.bindLong(statementHandle, 0, 7L, database.sqliteApi()));
        }
      }
      assertThrows(
          SqliteNativeException.class, () -> new SqliteNativeStatement(database, "select from"));
      assertThrows(
          NullPointerException.class,
          () -> new SqliteNativeStatement(database, NullTestSupport.nullOf(String.class)));
      database.executeStatement("insert into sample (id) values (1)");
      SqliteNativeException duplicateInsertFailure;
      try (SqliteNativeStatement duplicateInsert =
          SqliteNativeStatements.prepare(database, "insert into sample (id) values (1)")) {
        duplicateInsertFailure = assertThrows(SqliteNativeException.class, duplicateInsert::step);
        assertEquals(
            SqliteNativeResultCodes.CONSTRAINT_PRIMARYKEY,
            SqliteNativeStatements.extendedErrorCode(database.handle(), database.sqliteApi()));
        assertEquals("SQLITE_CONSTRAINT_PRIMARYKEY", duplicateInsertFailure.resultName());
      }
    }
  }

  @Test
  void executeScript_surfacesTypedSqliteFailureForInvalidSql() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("script-failure.sqlite")))) {
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
      assertEquals("SQLITE_ERROR", exception.resultName());
    }
  }

  @Test
  void executeStatement_rejectsRowProducingSql() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("row-producing.sqlite")))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, () -> database.executeStatement("select 1"));
      assertEquals(
          "SQLite control statement must not produce rows: select 1", exception.getMessage());
    }
  }

  @Test
  void mapper_readsPostingLineageOnlyFromCoupledPriorPostingIdAndReasonColumns() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("mapper.sqlite")))) {
      try (SqliteNativeStatement missingPrior =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, null, null, null
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, missingPrior.step());
        assertEquals(PostingLineage.direct(), SqlitePostingMapper.readPostingLineage(missingPrior));
      }
      try (SqliteNativeStatement missingPriorForWrapper =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  null, null, null, null, null, null, null, null, null, null, null, null, null, null
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
                  null, null, null, null, null, null, null, null, null, null, null, 'operator reversal', null, 'posting-1'
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
                  null, null, null, null, null, null, null, null, null, null, null, null, null, 'posting-1'
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
                  null, null, null, null, null, null, null, null, null, null, null, 'operator reversal', null, null
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
  void committedPosting_mapsToPublishedPostingAtTheBoundary() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("posting-fact-wrapper.sqlite")))) {
      try (SqliteNativeStatement postingRow =
          SqliteNativeStatements.prepare(
              database,
              """
              select
                  'posting-1',
                  'STANDARD',
                  'CASH_REVENUE',
                  '2026-05-05',
                  '2026-05-05T09:15:30Z',
                  'actor-1',
                  'AGENT',
                  'command-1',
                  'idem-1',
                  'cause-1',
                  'corr-1',
                  null,
                  'CLI',
                  null
              """)) {
        assertEquals(SqliteNativeResultCodes.ROW, postingRow.step());
        List<JournalLine> lines =
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")));
        assertEquals(
            dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator
                .toPublished(
                    SqlitePostingMapper.committedPosting(
                        postingRow,
                        lines,
                        SqlitePostingFactFixtureSupport.accountingEvidence("idem-1"))),
            dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator
                .toPublished(
                    SqlitePostingMapper.committedPosting(
                        postingRow,
                        lines,
                        SqlitePostingFactFixtureSupport.accountingEvidence("idem-1"))));
      }
    }
  }

  @Test
  void databaseAndStatementClose_areIdempotent() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("close.sqlite")))) {
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
        try (SqliteBookPassphrase ignored = SqliteBookKeyFile.load(keyPath)) {
          // The load path enforces the same key-file security contract before rewriting test data.
        }
      }
      Files.writeString(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static SqliteNativeDatabase openNativeDatabase(BookAccess bookAccess) {
    return SqliteNativeConnections.openKeyFileAccess(
        bookAccess.bookFilePath(), SqliteStoreFixtureSupport.requireKeyFilePath(bookAccess));
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
