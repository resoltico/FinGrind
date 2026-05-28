package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Bootstrap, close, and verification-lifecycle coverage for {@link SqlitePostingFactStore}. */
class SqliteStoreBootstrapAndLifecycleTest extends SqliteStoreLifecycleTestSupport {
  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteBookSchemaBootstrap.readSchema(this::failingInputStream));
  }

  @Test
  void initializeBook_executesWholeSchemaScriptWithoutStatementSplitting() {
    Path bookPath = tempDirectory.resolve("schema-script.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(
                      database,
                      () ->
                          new ByteArrayInputStream(
                              """
                              create table sample (
                                  id integer primary key,
                                  note text not null
                              );
                              create table sample_audit (
                                  note text not null
                              );
                              -- comment with semicolon;
                              create trigger sample_after_insert
                              after insert on sample
                              begin
                                  insert into sample_audit (note) values ('semi;colon');
                              end;
                              """
                                  .getBytes(StandardCharsets.UTF_8)));
                  database.executeStatement("insert into sample (id, note) values (1, 'ok')");
                  try (SqliteNativeStatement statement =
                      SqliteNativeStatements.prepare(database, "select note from sample_audit")) {
                    assertEquals(SqliteNativeResultCodes.ROW, statement.step());
                    assertEquals("semi;colon", statement.columnText(0));
                    assertEquals(SqliteNativeResultCodes.DONE, statement.step());
                  }
                }));
  }

  @Test
  void cachedValue_loadsAndStoresValueWhenCacheIsEmpty() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>();
    assertEquals("loaded", SqliteBookSchemaBootstrap.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>("cached");
    assertEquals(
        "cached",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              throw new AssertionError("loader should not run when cache already has a value");
            }));
  }

  @Test
  void cachedValue_returnsAlreadyPublishedValueWhenAnotherLoadWinsTheRace() {
    AtomicReference<@Nullable String> schemaCache = new AtomicReference<>();
    assertEquals(
        "published-first",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              schemaCache.set("published-first");
              return "loaded-late";
            }));
    assertEquals("published-first", schemaCache.get());
  }

  @Test
  void ensureParentDirectory_wrapsDirectoryPreparationFailures() {
    Path bookPath = tempDirectory.resolve("wrapped-parent").resolve("book.sqlite");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookSchemaBootstrap.ensureParentDirectory(
                    bookPath,
                    normalizedBookPath -> {
                      throw new java.io.IOException("boom");
                    }));

    assertEquals("Failed to create SQLite book directory.", exception.getMessage());
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void close_isIdempotent() {
    try (SqlitePostingFactStore postingFactStore =
        openStore(bookAccess(tempDirectory.resolve("close-ok.sqlite")))) {
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_afterDatabaseOpenRemainsIdempotent() throws Exception {
    Path bookPath = tempDirectory.resolve("close-opened.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      assertDoesNotThrow(() -> postingFactStore.listAccounts(firstAccountPage()));
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_zeroizesPendingPassphraseWhenDatabaseWasNeverOpened() throws Exception {
    SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters(
            "test close pending passphrase", TEST_BOOK_KEY.toCharArray());
    byte[] expectedZeroes = new byte[TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length];
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(tempDirectory.resolve("never-opened.sqlite"), passphrase)) {
      postingFactStore.close();
    }
    assertArrayEquals(expectedZeroes, passphraseBytes(passphrase));
  }

  @Test
  void storeRetainsStableOpenFailureAfterPassphraseConsumption() throws Exception {
    Path invalidBookPath = tempDirectory.resolve("invalid-retry.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(invalidBookPath))) {
      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      IllegalStateException secondFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new dev.erst.fingrind.core.AccountCode("1000")));
      assertProtectedBookVerificationFailure(firstFailure);
      assertSame(firstFailure, secondFailure);
    }
  }

  @Test
  void protectedBookVerificationFailure_coversCorruptedAndTruncatedProtectedBooks()
      throws Exception {
    Path intactBookPath = tempDirectory.resolve("intact-protected.sqlite");
    initializeBookOnDisk(intactBookPath);
    byte[] intactBytes = Files.readAllBytes(intactBookPath);
    Path corruptedBookPath = tempDirectory.resolve("corrupted-protected.sqlite");
    byte[] corruptedBytes = intactBytes.clone();
    corruptedBytes[Math.min(200, corruptedBytes.length - 1)] ^= 0x5A;
    Files.write(corruptedBookPath, corruptedBytes);
    Path truncatedBookPath = tempDirectory.resolve("truncated-protected.sqlite");
    Files.write(truncatedBookPath, Arrays.copyOf(intactBytes, 128));
    assertProtectedBookVerificationFailure(corruptedBookPath);
    assertProtectedBookVerificationFailure(truncatedBookPath);
  }

  @Test
  void protectedBookVerificationFailure_coversAllCanonicalVerificationResultCodes() {
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(SqliteNativeResultCodes.NOTADB, "not a database"))
            .isPresent());
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(
                    SqliteNativeResultCodes.IOERR_BADKEY, "cipher verification failed"))
            .isPresent());
    assertTrue(
        SqliteStoreOperations.protectedBookVerificationFailure(
                new SqliteNativeException(
                    SqliteNativeResultCodes.IOERR_CODEC, "codec verification failed"))
            .isPresent());
    assertEquals(
        Optional.empty(),
        SqliteStoreOperations.protectedBookVerificationFailure(
            new SqliteNativeException(SqliteNativeResultCodes.ERROR, "ordinary runtime failure")));
  }

  @Test
  void lifecycleOpen_wrapsNonVerificationNativeOpenFailures() {
    Path bookPath = tempDirectory.resolve("open-runtime-failure.sqlite");
    SqliteStoreContext context =
        new SqliteStoreContext(
            bookPath, SqliteStoreAccessMode.READ_ONLY, SqliteNativeBootstrap::api) {
          @Override
          SqliteNativeDatabase openConfiguredDatabase(SqliteBookPassphrase bookPassphrase) {
            throw new SqliteNativeException(SqliteNativeResultCodes.ERROR, "open-boom");
          }
        };
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "open runtime failure", TEST_BOOK_KEY.toCharArray()))) {
      SqliteStoreLifecycle lifecycle = new SqliteStoreLifecycle(context, sessionSecret);

      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, lifecycle::database);
      IllegalStateException repeatedFailure =
          assertThrows(IllegalStateException.class, lifecycle::database);

      assertInstanceOf(SqliteStorageFailureException.class, firstFailure);
      assertTrue(
          NullTestSupport.messageOf(firstFailure)
              .contains("Failed to open SQLite book connection. SQLITE_ERROR: open-boom"),
          () -> NullTestSupport.messageOf(firstFailure));
      assertSame(firstFailure, repeatedFailure);
    }
  }

  @Test
  void storeCloseSequence_close_acceptsNullSessionSecretAcrossBothClosePaths() {
    assertDoesNotThrow(
        () -> {
          try (SqliteStoreCloseSequence ignored = new SqliteStoreCloseSequence(null, null)) {
            // Close sequence runs on scope exit.
          }
        });

    AtomicReference<Boolean> databaseClosed = new AtomicReference<>(false);
    assertDoesNotThrow(
        () -> {
          try (SqliteStoreCloseSequence ignored =
              new SqliteStoreCloseSequence(
                  null,
                  new SqliteNativeDatabase(java.lang.foreign.MemorySegment.NULL) {
                    @Override
                    public void close() {
                      databaseClosed.set(true);
                    }
                  })) {
            // Close sequence runs on scope exit.
          }
        });
    assertTrue(databaseClosed.get());
  }

  @Test
  void storeCloseSequence_close_propagatesDatabaseCloseFailure() {
    AtomicReference<Boolean> closeAttempted = new AtomicReference<>(false);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> {
              try (SqliteStoreCloseSequence ignored =
                  new SqliteStoreCloseSequence(
                      null,
                      new SqliteNativeDatabase(java.lang.foreign.MemorySegment.NULL) {
                        @Override
                        public void close() {
                          closeAttempted.set(true);
                          throw new IllegalStateException("close boom");
                        }
                      })) {
                // Close sequence runs on scope exit.
              }
            });

    assertEquals("close boom", exception.getMessage());
    assertTrue(closeAttempted.get());
  }

  @Test
  void storeCloseSequence_close_closesSessionSecretWhenDatabaseCloseFails() {
    AtomicReference<Boolean> closeAttempted = new AtomicReference<>(false);
    try (SqliteSessionSecret sessionSecret =
        new SqliteSessionSecret(
            SqliteBookPassphrase.fromCharacters(
                "close sequence failing database", TEST_BOOK_KEY.toCharArray()))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> {
                try (SqliteStoreCloseSequence ignored =
                    new SqliteStoreCloseSequence(
                        sessionSecret::close,
                        new SqliteNativeDatabase(java.lang.foreign.MemorySegment.NULL) {
                          @Override
                          public void close() {
                            closeAttempted.set(true);
                            throw new IllegalStateException("close boom");
                          }
                        })) {
                  // Close sequence runs on scope exit.
                }
              });

      assertEquals("close boom", exception.getMessage());
      assertTrue(closeAttempted.get());
      assertThrows(IllegalStateException.class, sessionSecret::borrowWorkingCopy);
    }
  }
}
