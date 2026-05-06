package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests for the SQLite FFM binding layer. */
@NullUnmarked
class SqliteNativeOpenAndRekeyTest extends SqliteNativeBridgeTestSupport {

  @Test
  void open_rejectsNullBookAccess() {
    assertThrows(NullPointerException.class, () -> SqliteNativeConnections.open(null));
  }

  @Test
  void open_rejectsNonKeyFileAccessSelection() {
    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () ->
                SqliteNativeConnections.open(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(),
        exception.failure().code());
    assertEquals(
        "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-stdin.",
        exception.failure().message());
  }

  @Test
  void requireKeyFile_rejectsInteractivePromptSelection() {
    ContractDecision<Path> decision =
        SqliteBookAccessRules.requireKeyFile(
            new BookAccess(
                tempDirectory.resolve("prompt-access.sqlite"),
                BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));

    switch (decision) {
      case ContractDecision.Accepted<Path>(Path ignored) ->
          throw new AssertionError("Expected prompt selection to be rejected.");
      case ContractDecision.Rejected<Path>(ContractFailure failure) -> {
        assertEquals(
            ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(), failure.code());
        assertEquals(
            "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-prompt.",
            failure.message());
      }
    }
  }

  @Test
  void open_rejectsInvalidKeyFilePayloadBeforeNativeOpen() throws Exception {
    Path bookPath = tempDirectory.resolve("invalid-key-payload.sqlite");
    Path keyPath = tempDirectory.resolve("invalid-key-payload.key");
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
    Files.write(keyPath, new byte[] {(byte) 0xFF});

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeConnections.open(
                    new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath))));

    assertTrue(exception.getMessage().contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void open_wrapsRejectedKeyFileDecisionAsContractFailure() {
    Path bookPath = tempDirectory.resolve("missing-key-file.sqlite");
    Path missingKeyPath = tempDirectory.resolve("missing-key-file.key");

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () ->
                SqliteNativeConnections.open(
                    new BookAccess(
                        bookPath, new BookAccess.PassphraseSource.KeyFile(missingKeyPath))));

    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.code(), exception.failure().code());
    assertTrue(exception.getMessage().contains("does not exist"));
  }

  @Test
  void enforceBookFilePermissions_wrapsIoFailuresAsStorageFailures() {
    Path bookPath = tempDirectory.resolve("permission-hardening.sqlite");

    SqliteStorageFailureException exception =
        assertThrows(
            SqliteStorageFailureException.class,
            () ->
                SqliteNativeConnections.enforceBookFilePermissions(
                    bookPath,
                    ignored -> {
                      throw new IOException("chmod failed");
                    }));

    assertTrue(exception.getMessage().contains("book file permissions"));
    assertEquals("chmod failed", exception.getCause().getMessage());
  }

  @Test
  void openExecutePrepareAndClose_roundTripThroughSystemLibrary() throws Exception {
    Path bookPath = tempDirectory.resolve("native-round-trip.sqlite");

    assertDoesNotThrow(
        () ->
            withOpenDatabase(
                bookAccess(bookPath),
                database -> {
                  database.executeStatement(
                      "create table sample (id integer not null, note text null)");

                  try (SqliteNativeStatement insert =
                      SqliteNativeStatements.prepare(
                          database, "insert into sample (id, note) values (?, ?)")) {
                    insert.bindInt(1, 7);
                    insert.bindText(2, null);
                    assertEquals(SqliteNativeResultCodes.DONE, insert.step());
                  }

                  try (SqliteNativeStatement select =
                      SqliteNativeStatements.prepare(database, "select id, note from sample")) {
                    assertEquals(SqliteNativeResultCodes.ROW, select.step());
                    assertEquals(7, select.columnInt(0));
                    assertNull(select.columnText(1));
                    assertEquals(SqliteNativeResultCodes.DONE, select.step());
                  }
                }));
  }

  @Test
  void utf8ByteLength_usesNativeSegmentSizeWithoutNullTerminator() {
    String value = "Riga € 漢字";

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment valuePointer = arena.allocateFrom(value);

      assertEquals(
          value.getBytes(StandardCharsets.UTF_8).length,
          SqliteNativeStatement.utf8ByteLength(valuePointer));
      assertEquals(value.getBytes(StandardCharsets.UTF_8).length + 1L, valuePointer.byteSize());
    }
  }

  @Test
  void open_throwsForDirectoryTarget() throws Exception {
    Path directoryPath = tempDirectory.resolve("not-a-book");
    java.nio.file.Files.createDirectories(directoryPath);

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () -> SqliteNativeConnections.open(bookAccess(directoryPath)));

    assertTrue(exception.resultName().contains("SQLITE_CANTOPEN"));
  }

  @Test
  void open_rejectsWrongBookKey() throws Exception {
    Path bookPath = tempDirectory.resolve("wrong-key.sqlite");

    withOpenDatabase(
        bookAccess(bookPath, TEST_BOOK_KEY),
        database -> database.executeStatement("create table sample (id integer not null)"));

    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () -> SqliteNativeConnections.open(bookAccess(bookPath, "different-book-key")));

    assertTrue(exception.resultName().contains("SQLITE_NOTADB"));
    assertFalse(String.valueOf(exception.getMessage()).contains("different-book-key"));
  }

  @Test
  void openOverloadAndRekey_rotateBookPassphrase() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native.sqlite");

    try (SqliteBookPassphrase initialPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "initial native passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, initialPassphrase)) {
      database.executeStatement("create table sample (id integer primary key, note text not null)");
      database.executeStatement("insert into sample (id, note) values (1, 'ok')");

      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement native passphrase", "rotated-key".toCharArray())) {
        SqliteNativeConnections.rekey(database, replacementPassphrase);
      }
    }

    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement native passphrase", "rotated-key".toCharArray());
        SqliteNativeDatabase reopened =
            SqliteNativeConnections.open(bookPath, replacementPassphrase)) {
      try (SqliteNativeStatement statement =
          SqliteNativeStatements.prepare(reopened, "select count(*) from sample")) {
        assertEquals(SqliteNativeResultCodes.ROW, statement.step());
        assertEquals(1, statement.columnInt(0));
      }
    }

    try (SqliteBookPassphrase oldPassphrase =
        SqliteBookPassphrase.fromCharacters(
            "stale native passphrase", TEST_BOOK_KEY.toCharArray())) {
      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () -> SqliteNativeConnections.open(bookPath, oldPassphrase));

      assertEquals("SQLITE_NOTADB", exception.resultName());
    }
  }

  @Test
  void rekey_rejectsNullArguments() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-nulls.sqlite");

    assertThrows(NullPointerException.class, () -> SqliteNativeConnections.rekey(null, null));
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey null passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database = SqliteNativeConnections.open(bookPath, passphrase)) {
      assertThrows(NullPointerException.class, () -> SqliteNativeConnections.rekey(database, null));
    }
  }

  @Test
  void rekey_rethrowsSqliteNativeExceptionFromNativeFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native failure passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withRekey(
                    SqliteNativeBootstrap.api(),
                    constantMethodHandle(
                        14, MemorySegment.class, MemorySegment.class, int.class)))) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "native failure replacement", "rotated-key".toCharArray())) {
        SqliteNativeException exception =
            assertThrows(
                SqliteNativeException.class,
                () -> SqliteNativeConnections.rekey(database, replacementPassphrase));

        assertEquals("SQLITE_CANTOPEN", exception.resultName());
      }
    }
  }

  @Test
  void rekey_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-throwable.sqlite");

    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "throwable passphrase", TEST_BOOK_KEY.toCharArray());
        SqliteNativeDatabase database =
            SqliteNativeConnections.open(
                bookPath,
                passphrase,
                SqliteNativeOpenMode.READ_WRITE_CREATE,
                SqliteNativeApiTestSupport.withRekey(
                    SqliteNativeBootstrap.api(),
                    throwingMethodHandle(
                        new IllegalStateException("boom"),
                        int.class,
                        MemorySegment.class,
                        MemorySegment.class,
                        int.class)))) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "throwable replacement", "rotated-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteNativeConnections.rekey(database, replacementPassphrase));

        assertTrue(
            exception
                .getMessage()
                .contains(
                    "Failed to rekey the FinGrind SQLite book with passphrase material from"));
        assertEquals("boom", exception.getCause().getMessage());
        assertFalse(exception.getMessage().contains("rotated-key"));
      }
    }
  }

  @Test
  void applyKey_wrapsUnexpectedThrowableFromNativeInvocation() throws Exception {
    Path keyFile = tempDirectory.resolve("apply-key.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              throwingMethodHandle(
                  new IllegalStateException("boom"),
                  int.class,
                  MemorySegment.class,
                  MemorySegment.class,
                  int.class),
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(MemorySegment.NULL, MemorySegment.class),
              constantMethodHandle(MemorySegment.NULL, int.class),
              constantMethodHandle(0, MemorySegment.class));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteNativeConnections.applyKey(
                      MemorySegment.NULL, keyMaterial, sqliteApi, arena));

      assertTrue(
          exception
              .getMessage()
              .contains("Failed to apply the FinGrind SQLite book passphrase from"));
      assertFalse(exception.getMessage().contains(TEST_BOOK_KEY));
    }
  }

  @Test
  void applyKey_rethrowsSqliteNativeException() throws Exception {
    Path keyFile = tempDirectory.resolve("apply-key-native-failure.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      SqliteNativeApi sqliteApi =
          sqliteApi(
              constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class),
              constantMethodHandle(0, MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), MemorySegment.class),
              constantMethodHandle(arena.allocateFrom("boom"), int.class),
              constantMethodHandle(14, MemorySegment.class));

      SqliteNativeException exception =
          assertThrows(
              SqliteNativeException.class,
              () ->
                  SqliteNativeConnections.applyKey(
                      MemorySegment.NULL, keyMaterial, sqliteApi, arena));

      assertEquals("SQLITE_CANTOPEN: boom", exception.getMessage());
    }
  }
}
