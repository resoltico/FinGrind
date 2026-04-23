package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingValidation;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteStoreLifecycleAndAccessModeTest extends SqlitePostingFactStoreTestSupport {

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
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals("loaded", SqliteBookSchemaBootstrap.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<String> schemaCache = new AtomicReference<>("cached");

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
    AtomicReference<String> schemaCache = new AtomicReference<>();

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
  void close_isIdempotent() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("close-ok.sqlite")))) {
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_afterDatabaseOpenRemainsIdempotent() throws Exception {
    Path bookPath = tempDirectory.resolve("close-opened.sqlite");
    initializeBookOnDisk(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
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

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      IllegalStateException secondFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertInvalidPlaintextBookFailure(firstFailure);
      assertSame(firstFailure, secondFailure);
    }
  }

  @Test
  void accessModes_enforceWritableBoundariesAndQueryOnlyPolicy() throws Exception {
    assertEquals(1, SqliteStoreAccessMode.READ_ONLY.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_EXISTING.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_CREATE.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.PLAN_EXECUTION.queryOnlyPragmaValue());

    assertThrows(
        IllegalStateException.class, SqliteStoreAccessMode.READ_ONLY::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableMutation);

    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_ONLY::requireWritableInitialization);
    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableInitialization);
    assertTrue(SqliteStoreAccessMode.PLAN_EXECUTION.preservesMissingBookStateUntilMutation());
    assertTrue(SqliteStoreAccessMode.READ_WRITE_CREATE.preservesMissingBookStateUntilMutation());

    Path existingBookPath = tempDirectory.resolve("read-write-existing.sqlite");
    initializeBookOnDisk(existingBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                existingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("3000"),
                  new AccountName("Equity"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("3000"),
              new AccountName("Equity"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    Path missingBookPath = tempDirectory.resolve("read-write-existing-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode missing", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                missingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          "This FinGrind SQLite session cannot initialize or create a book file.",
          exception.getMessage());
    }
  }

  @Test
  void helperBoundaries_rejectUnsafeShapesAndWrapNativeFailures() throws Exception {
    SqliteBookStateReader bookStateReader =
        new SqliteBookStateReader(
            SqliteBookContract.APPLICATION_ID,
            SqliteBookContract.FORMAT_VERSION,
            "account",
            "book_meta",
            "journal_line",
            "posting_fact");

    Path blankBookPath = tempDirectory.resolve("helper-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    withStandaloneDatabase(
        bookAccess(blankBookPath),
        database -> {
          IllegalStateException emptyQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleInt(database, "select 1 where 0"));
          assertEquals(
              "SQLite integer query returned no rows: select 1 where 0",
              emptyQueryException.getMessage());

          IllegalStateException emptyTextQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleText(database, "select 'x' where 0"));
          assertEquals(
              "SQLite text query returned no rows: select 'x' where 0",
              emptyTextQueryException.getMessage());

          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(blankBookPath))) {
            IllegalStateException blankException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not initialized as a FinGrind book.",
                blankException.getMessage());
          }
        });

    Path initializedBookPath = tempDirectory.resolve("helper-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          IllegalStateException multiRowException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.queryOptionalInt(
                          database, "select 1 union all select 2"));
          assertEquals(
              "SQLite integer query returned more than one row: select 1 union all select 2",
              multiRowException.getMessage());

          IllegalStateException multiRowTextException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.querySingleText(
                          database, "select 'x' union all select 'y'"));
          assertEquals(
              "SQLite text query returned more than one row: select 'x' union all select 'y'",
              multiRowTextException.getMessage());
          assertEquals(
              OptionalInt.of(1), SqliteStatementQueries.queryOptionalInt(database, "select 1"));
          assertEquals("x", SqliteStatementQueries.querySingleText(database, "select 'x'"));
          assertEquals("INITIALIZED_FINGRIND", bookStateReader.bookState(database).toString());
        });

    Path foreignBookPath = tempDirectory.resolve("helper-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    withStandaloneDatabase(
        bookAccess(foreignBookPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
            IllegalStateException foreignException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not a FinGrind book.", foreignException.getMessage());
          }
        });

    Path unsupportedBookPath = tempDirectory.resolve("helper-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> {
          assertEquals(
              "UNSUPPORTED_FINGRIND_VERSION", bookStateReader.bookState(database).toString());
        });

    Path incompleteBookPath = tempDirectory.resolve("helper-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    withStandaloneDatabase(
        bookAccess(incompleteBookPath),
        database -> {
          assertEquals("INCOMPLETE_FINGRIND", bookStateReader.bookState(database).toString());
        });

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeConnections.open(bookAccess(blankBookPath)));
      assertEquals(
          Optional.of(new PostingRejection.BookNotInitialized()),
          PostingValidation.rejectionFor(
              postingDraft("posting-helper", "idem-helper", Optional.empty(), Optional.empty()),
              new SqliteTransactionValidationBook(
                  storeDatabase(postingFactStore), postingFactStore.postingReader())));
    }

    Path staleBookPath = tempDirectory.resolve("find-one-stale.sqlite");
    createEmptySqliteFile(staleBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(staleBookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(staleBookPath));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-helper")));
      assertTrue(failure.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void activeNativeDatabase_returnsPublishedSessionHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("active-native-database.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      assertEquals(storeDatabase(postingFactStore), postingFactStore.activeNativeDatabase());
    }
  }
}
