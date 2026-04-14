package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.application.BookAdministrationRejection;
import dev.erst.fingrind.application.DeclareAccountResult;
import dev.erst.fingrind.application.DeclaredAccount;
import dev.erst.fingrind.application.OpenBookResult;
import dev.erst.fingrind.application.PostingCommitResult;
import dev.erst.fingrind.application.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqlitePostingFactStoreTest {
  private static final String TEST_BOOK_KEY = "posting-fact-store-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void findByIdempotency_returnsEmptyWhenPostingIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-posting.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findReversalFor_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void commit_returnsBookNotInitializedWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-commit.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new PostingCommitResult.BookNotInitialized(),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void openBook_rejectsSQLiteFileThatAlreadyContainsSchema() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("legacy.sqlite");
    createPostingFactOnlyBook(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema()),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }
  }

  @Test
  void declareAccount_requiresInitializedBook() {
    Path databasePath = tempDirectory.resolve("declare-uninitialized.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void storeOperations_handleMissingAndRawUninitializedSqliteBooks() throws SqliteNativeException {
    Path missingBookPath = tempDirectory.resolve("missing-ops.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(List.of(), postingFactStore.listAccounts());
      assertEquals(
          Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new PostingCommitResult.BookNotInitialized(),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertFalse(Files.exists(missingBookPath));
    }

    Path rawSqlitePath = tempDirectory.resolve("raw-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(rawSqlitePath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(List.of(), postingFactStore.listAccounts());
      assertEquals(
          Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new PostingCommitResult.BookNotInitialized(),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void storeOperations_wrapFailuresForInvalidBookFiles() throws IOException {
    Path invalidBookPath = tempDirectory.resolve("not-a-sqlite-file.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.declareAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::listAccounts);

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByPostingId(new PostingId("posting-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void openBook_rejectsAlreadyInitializedBook() {
    Path databasePath = tempDirectory.resolve("already-initialized.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
          postingFactStore.openBook(Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void schemaOnlyBook_reportsUninitializedStateAndEmptyAccountLookup()
      throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("schema-only.sqlite");
    createSchemaOnlyBook(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void declareAccount_listsAndReactivatesAccounts() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("declare-accounts.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));

      deactivateAccount(databasePath, "1000");

      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash main"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          List.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.listAccounts());
    }
  }

  @Test
  void declareAccount_rejectsNormalBalanceConflict() {
    Path databasePath = tempDirectory.resolve("declare-conflict.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new DeclareAccountResult.Rejected(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void commit_returnsUnknownAndInactiveAccountOutcomes() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("account-rejections.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new PostingCommitResult.UnknownAccount(new AccountCode("1000")),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      declareDefaultAccounts(postingFactStore);
      deactivateAccount(databasePath, "1000");

      assertEquals(
          new PostingCommitResult.InactiveAccount(new AccountCode("1000")),
          postingFactStore.commit(
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commitAndFinders_roundTripPostingWithoutReversal() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(postingFact), postingFactStore.commit(postingFact));
      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
      assertEquals(
          Optional.of(postingFact), postingFactStore.findByPostingId(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void openBook_initializesCanonicalTablesAsStrict() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('book_meta') where name = 'book_meta'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('account') where name = 'account'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('journal_line') where name = 'journal_line'"));
        });
  }

  @Test
  void openBook_configuresOpenConnectionForForeignKeysAndUntrustedSchema() throws Exception {
    Path databasePath = tempDirectory.resolve("connection-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
    }
  }

  @Test
  void canonicalStrictSchema_rejectsNonLosslessTypeMismatches() throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("strict-datatype.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteSchemaManager.initializeBook(database);
                  insertInitializedAtRow(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-1", "idem-1");

                  SqliteNativeException exception =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into journal_line (
                                      posting_id,
                                      line_order,
                                      account_code,
                                      entry_side,
                                      currency_code,
                                      amount
                                  ) values (
                                      'posting-1',
                                      'not-an-integer',
                                      '1000',
                                      'DEBIT',
                                      'EUR',
                                      '10.00'
                                  )
                                  """));

                  assertEquals(
                      SqliteNativeLibrary.SQLITE_CONSTRAINT_DATATYPE, exception.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_DATATYPE", exception.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void findByPostingId_returnsEmptyWhenExistingBookHasNoMatchingPosting() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.empty(), postingFactStore.findByPostingId(new PostingId("posting-missing")));
    }
  }

  @Test
  void commitAndFindByIdempotency_preservesReversalReference() {
    Path databasePath = tempDirectory.resolve("nested").resolve("entity-b.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact reversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(originalFact);
      postingFactStore.commit(reversalFact);

      assertEquals(
          Optional.of(reversalFact),
          postingFactStore.findByIdempotency(new IdempotencyKey("idem-2")));
    }
  }

  @Test
  void commit_returnsDuplicateIdempotencyOutcome() {
    Path databasePath = tempDirectory.resolve("fingrind.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingFact);

      assertEquals(
          new PostingCommitResult.DuplicateIdempotency(new IdempotencyKey("idem-1")),
          postingFactStore.commit(
              postingFact("posting-2", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commit_returnsDuplicateReversalTargetOutcome() {
    Path databasePath = tempDirectory.resolve("reversal.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact firstReversal =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));
    PostingFact secondReversal =
        postingFact(
            "posting-3",
            "idem-3",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("another full reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(originalFact);
      postingFactStore.commit(firstReversal);

      assertEquals(
          new PostingCommitResult.DuplicateReversalTarget(new PostingId("posting-1")),
          postingFactStore.commit(secondReversal));
      assertEquals(
          Optional.of(firstReversal), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commit_throwsWhenPostingIdAlreadyExistsWithDifferentIdempotencyKey() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact("posting-1", "idem-2", Optional.empty(), Optional.empty())));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  void commit_throwsWhenSqliteFailureIsNotMappedToAnOrdinaryOutcome() {
    Path databasePath = tempDirectory.resolve("unexpected.sqlite");
    PostingFact invalidReversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-missing"))),
            Optional.of(new ReversalReason("operator reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.commit(invalidReversalFact));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      assertTrue(exception.getMessage().contains("SQLITE_CONSTRAINT_FOREIGNKEY"));
    }
  }

  @Test
  void commit_rejectsBookPathWhoseParentIsAFile() throws IOException {
    Path fileParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(fileParent, "nope", StandardCharsets.UTF_8);
    Path keyPath = tempDirectory.resolve("book-keys").resolve("entity.book-key");
    Files.createDirectories(keyPath.getParent());
    Files.writeString(keyPath, TEST_BOOK_KEY, StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            new BookAccess(
                fileParent.resolve("entity.sqlite"),
                new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to create SQLite book directory."));
    }
  }

  @Test
  void findByIdempotency_throwsWhenExistingBookFileIsNotSqlite() throws IOException {
    Path databasePath = tempDirectory.resolve("not-a-database.sqlite");
    Files.writeString(databasePath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));

      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void findByIdempotency_throwsWhenBookPathPointsAtDirectory() throws IOException {
    Path databasePath = tempDirectory.resolve("book-directory");
    Files.createDirectories(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("missing-idem")));

      assertTrue(exception.getMessage().contains("Failed to open SQLite book connection."));
    }
  }

  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteSchemaManager.readSchema(SqlitePostingFactStoreTest::failingInputStream));
  }

  @Test
  void initializeBook_executesWholeSchemaScriptWithoutStatementSplitting()
      throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("schema-script.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteSchemaManager.initializeBook(
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
                      SqliteNativeLibrary.prepare(database, "select note from sample_audit")) {
                    assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
                    assertEquals("semi;colon", statement.columnText(0));
                    assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
                  }
                }));
  }

  @Test
  void cachedValue_loadsAndStoresValueWhenCacheIsEmpty() {
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals("loaded", SqliteSchemaManager.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<String> schemaCache = new AtomicReference<>("cached");

    assertEquals(
        "cached",
        SqliteSchemaManager.cachedValue(
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
        SqliteSchemaManager.cachedValue(
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
      assertDoesNotThrow(postingFactStore::listAccounts);
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
  void constructor_rejectsNonKeyFileAccessSelection() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "SQLite same-package file-backed stores require a --book-key-file access selection.",
        exception.getMessage());
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void passphraseFor_loadsKeyFileBackedAccessSelection() throws Exception {
    Path keyFile = tempDirectory.resolve("book-passphrase.key");
    Files.writeString(keyFile, TEST_BOOK_KEY, StandardCharsets.UTF_8);
    Method method =
        SqlitePostingFactStore.class.getDeclaredMethod("passphraseFor", BookAccess.class);
    method.setAccessible(true);

    try (SqliteBookPassphrase passphrase =
        (SqliteBookPassphrase)
            method.invoke(
                null,
                new BookAccess(
                    tempDirectory.resolve("book-passphrase.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(keyFile)))) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void configureOpenedDatabase_closesUnconfiguredDatabaseQuietlyWhenPragmasFail() throws Exception {
    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqlitePostingFactStore.configureOpenedDatabase(
                    staleDatabaseHandle(tempDirectory.resolve("stale.sqlite"))));

    assertFalse(exception.getMessage().isBlank());
  }

  @Test
  void closeAfterConfigurationFailure_closesOpenDatabase() throws Exception {
    Path bookPath = tempDirectory.resolve("configured-close.sqlite");
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath));

    assertDoesNotThrow(() -> SqlitePostingFactStore.closeAfterConfigurationFailure(database));
  }

  @Test
  void closeAfterConfigurationFailure_ignoresNativeCloseFailure() throws Exception {
    assertDoesNotThrow(
        () ->
            SqlitePostingFactStore.closeAfterConfigurationFailure(
                staleDatabaseHandle(tempDirectory.resolve("stale-close.sqlite"))));
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void takeBookPassphrase_rejectsSecondConsumption() throws Exception {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("passphrase-consumption.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test passphrase consumption", TEST_BOOK_KEY.toCharArray()))) {
      Method method = SqlitePostingFactStore.class.getDeclaredMethod("takeBookPassphrase");
      method.setAccessible(true);

      try (SqliteBookPassphrase ignored = (SqliteBookPassphrase) method.invoke(postingFactStore)) {
        InvocationTargetException exception =
            assertThrows(InvocationTargetException.class, () -> method.invoke(postingFactStore));

        assertEquals(
            "SQLite book passphrase is no longer available.", exception.getCause().getMessage());
      }
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void openBook_remembersConsumedPassphraseFailureAsTerminalState() throws Exception {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("consumed-passphrase.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test consumed passphrase", TEST_BOOK_KEY.toCharArray()))) {
      Method method = SqlitePostingFactStore.class.getDeclaredMethod("takeBookPassphrase");
      method.setAccessible(true);

      try (SqliteBookPassphrase ignored = (SqliteBookPassphrase) method.invoke(postingFactStore)) {
        IllegalStateException firstFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
        IllegalStateException secondFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

        assertEquals("SQLite book passphrase is no longer available.", firstFailure.getMessage());
        assertSame(firstFailure, secondFailure);
      }
    }
  }

  @Test
  void close_rejectsFurtherUse() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("closed.sqlite")))) {
      postingFactStore.close();

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-closed")));

      assertEquals("SQLite book session is already closed.", exception.getMessage());
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void close_wrapsNativeDatabaseCloseFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, postingFactStore::close);

    assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void openBook_wrapsInitializationFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("schema-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

    assertTrue(exception.getMessage().contains("Failed to initialize SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void commit_ignoresRollbackFailureWhenPrimaryFailureAlreadyExists() throws Exception {
    Path bookPath = tempDirectory.resolve("rollback-native-failure.sqlite");
    initializeBookOnDisk(bookPath);
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                postingFactStore.commit(
                    postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

    assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
  }

  @Test
  void commit_primaryKeyConflictWithFirstReversalLeavesConstraintAsPrimaryFailure() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact(
                          "posting-1",
                          "idem-2",
                          Optional.of(new ReversalReference(new PostingId("posting-1"))),
                          Optional.of(new ReversalReason("full reversal")))));

      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void findByIdempotency_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("query-native-failure.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-query")));

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void isInitialized_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("initialized-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, postingFactStore::isInitialized);

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void findAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.findAccount(new AccountCode("1000")));

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void declareAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("declare-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                postingFactStore.declareAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    Instant.parse("2026-04-07T10:15:30Z")));

    assertTrue(exception.getMessage().contains("Failed to declare SQLite book account."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void listAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("list-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, postingFactStore::listAccounts);

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void findByPostingId_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("posting-id-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.findByPostingId(new PostingId("posting-1")));

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void findReversalFor_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("reversal-stale-handle.sqlite");
    SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookAccess(bookPath));
    setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> postingFactStore.findReversalFor(new PostingId("posting-1")));

    assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
  }

  @Test
  void findByIdempotency_wrapsJournalLineLoadFailureAfterPostingRowMatch()
      throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-partial")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void executeFindOnePosting_closesStatementWhenRowMappingFails() throws Exception {
    Path bookPath = tempDirectory.resolve("row-mapping-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeLibrary.open(bookAccess(bookPath)));

      Class<?> binderType =
          Class.forName("dev.erst.fingrind.sqlite.SqlitePostingFactStore$SqliteBinder");
      Method method =
          SqlitePostingFactStore.class.getDeclaredMethod(
              "executeFindOnePosting", SqliteNativeDatabase.class, String.class, binderType);
      method.setAccessible(true);
      ClassLoader proxyClassLoader =
          Optional.ofNullable(Thread.currentThread().getContextClassLoader())
              .orElseGet(SqlitePostingFactStoreTest.class::getClassLoader);
      Object binder =
          Proxy.newProxyInstance(
              proxyClassLoader,
              new Class<?>[] {binderType},
              (proxy, invokedMethod, arguments) -> null);

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  method.invoke(
                      postingFactStore,
                      storeDatabase(postingFactStore),
                      "select null as posting_id",
                      binder));

      assertTrue(exception.getCause() instanceof NullPointerException);
    }
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }

  private static int queryInt(SqliteNativeDatabase database, String sql)
      throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
      int value = statement.columnInt(0);
      assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
      return value;
    }
  }

  private static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(reversalReference),
        reversalReference,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1")),
                reason),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static void initializeBookWithDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    declareDefaultAccounts(postingFactStore);
  }

  private static void declareDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            NormalBalance.CREDIT,
            Instant.parse("2026-04-07T10:15:30Z")));
  }

  private static JournalEntry journalEntry(Optional<ReversalReference> reversalReference) {
    if (reversalReference.isPresent()) {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
              line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
    }
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey)
      throws SqliteNativeException {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            effective_date,
            recorded_at,
            actor_id,
            actor_type,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id
        ) values (
            '%s',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            'actor-1',
            'AGENT',
            'command-%s',
            '%s',
            'cause-1',
            null,
            null,
            'CLI',
            null
        )
        """
            .formatted(postingId, postingId, idempotencyKey));
  }

  private static void insertInitializedAtRow(SqliteNativeDatabase database)
      throws SqliteNativeException {
    database.executeStatement(
        """
        insert into book_meta (key, value)
        values ('initialized_at', '2026-04-07T10:15:30Z')
        """);
  }

  private static void insertAccountRow(
      SqliteNativeDatabase database,
      String accountCode,
      String accountName,
      String normalBalance,
      int active,
      String declaredAt)
      throws SqliteNativeException {
    database.executeStatement(
        """
        insert into account (
            account_code,
            account_name,
            normal_balance,
            active,
            declared_at
        ) values (
            '%s',
            '%s',
            '%s',
            %d,
            '%s'
        )
        """
            .formatted(accountCode, accountName, normalBalance, active, declaredAt));
  }

  @SuppressWarnings("PMD.SignatureDeclareThrowsException")
  private SqliteNativeDatabase staleDatabaseHandle(Path bookPath) throws Exception {
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath));
    MemorySegment handle = database.handle();
    closeStandaloneDatabase(database);
    return new SqliteNativeDatabase(handle);
  }

  private static void createPostingFactOnlyBook(Path bookPath) throws SqliteNativeException {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement(
              """
              create table posting_fact (
                  posting_id text primary key,
                  effective_date text not null,
                  recorded_at text not null,
                  actor_id text not null,
                  actor_type text not null,
                  command_id text not null,
                  idempotency_key text not null unique,
                  causation_id text not null,
                  correlation_id text null,
                  reason text null,
                  source_channel text not null,
                  prior_posting_id text null
              )
              """);
          database.executeStatement(
              """
              insert into posting_fact (
                  posting_id,
                  effective_date,
                  recorded_at,
                  actor_id,
                  actor_type,
                  command_id,
                  idempotency_key,
                  causation_id,
                  correlation_id,
                  reason,
                  source_channel,
                  prior_posting_id
              ) values (
                  'posting-partial',
                  '2026-04-07',
                  '2026-04-07T10:15:30Z',
                  'actor-1',
                  'AGENT',
                  'command-partial',
                  'idem-partial',
                  'cause-1',
                  null,
                  null,
                  'CLI',
                  null
              )
              """);
        });
  }

  private static void createEmptySqliteFile(Path bookPath) throws SqliteNativeException {
    withStandaloneDatabase(staticBookAccess(bookPath), database -> {});
  }

  private static void createSchemaOnlyBook(Path bookPath) throws SqliteNativeException {
    withStandaloneDatabase(staticBookAccess(bookPath), SqliteSchemaManager::initializeBook);
  }

  private static void initializeBookOnDisk(Path bookPath) throws SqliteNativeException {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          SqliteSchemaManager.initializeBook(database);
          insertInitializedAtRow(database);
          insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
          insertAccountRow(database, "2000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
        });
  }

  private static void deactivateAccount(Path bookPath, String accountCode)
      throws SqliteNativeException {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database ->
            database.executeStatement(
                """
                update account
                set active = 0
                where account_code = '%s'
                """
                    .formatted(accountCode)));
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static void setStoreDatabase(
      SqlitePostingFactStore postingFactStore, SqliteNativeDatabase database) throws Exception {
    Field field = SqlitePostingFactStore.class.getDeclaredField("database");
    field.setAccessible(true);
    field.set(postingFactStore, database);
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static SqliteNativeDatabase storeDatabase(SqlitePostingFactStore postingFactStore)
      throws Exception {
    Field field = SqlitePostingFactStore.class.getDeclaredField("database");
    field.setAccessible(true);
    return (SqliteNativeDatabase) field.get(postingFactStore);
  }

  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.SignatureDeclareThrowsException"})
  private static byte[] passphraseBytes(SqliteBookPassphrase passphrase) throws Exception {
    Field field = SqliteBookPassphrase.class.getDeclaredField("utf8Bytes");
    field.setAccessible(true);
    return ((byte[]) field.get(passphrase)).clone();
  }

  private static void closeStandaloneDatabase(SqliteNativeDatabase database)
      throws SqliteNativeException {
    database.close();
  }

  private static void withStandaloneDatabase(BookAccess bookAccess, SqliteDatabaseAction action)
      throws SqliteNativeException {
    SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess);
    try {
      action.run(database);
    } finally {
      closeStandaloneDatabase(database);
    }
  }

  private BookAccess bookAccess(Path bookPath) {
    try {
      Path keyDirectory = tempDirectory.resolve("book-keys");
      Files.createDirectories(keyDirectory);
      Path keyPath = keyDirectory.resolve(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      Files.writeString(keyPath, TEST_BOOK_KEY, StandardCharsets.UTF_8);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static BookAccess staticBookAccess(Path bookPath) {
    try {
      Path keyPath = Files.createTempFile("fingrind-book-key-", ".key");
      keyPath.toFile().deleteOnExit();
      Files.writeString(keyPath, TEST_BOOK_KEY, StandardCharsets.UTF_8);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void assertInvalidPlaintextBookFailure(IllegalStateException exception) {
    assertTrue(exception.getMessage().contains("Failed to open SQLite book connection."));
    assertTrue(exception.getMessage().contains("SQLITE_NOTADB"));
  }

  /** Performs one checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  private interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database) throws SqliteNativeException;
  }
}
