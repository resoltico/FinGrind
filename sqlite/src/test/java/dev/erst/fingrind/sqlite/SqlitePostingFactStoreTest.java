package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
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
  void schemaOnlyBook_isRejectedAsIncompleteFinGrindBook() throws SqliteNativeException {
    Path databasePath = tempDirectory.resolve("schema-only.sqlite");
    createSchemaOnlyBook(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertTrue(
          initializedException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(
          accountException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));

      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(
          openException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));
    }
  }

  @Test
  void openBook_setsFinGrindIdentityAndHardeningPragmas() throws Exception {
    Path databasePath = tempDirectory.resolve("identity-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma secure_delete"));
      assertEquals(2, queryInt(storeDatabase(postingFactStore), "pragma temp_store"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              SqlitePostingFactStore.BOOK_APPLICATION_ID,
              queryInt(database, "pragma application_id"));
          assertEquals(
              SqlitePostingFactStore.BOOK_FORMAT_VERSION,
              queryInt(database, "pragma user_version"));
        });
  }

  @Test
  void foreignAndUnsupportedBooks_areRejectedAcrossBoundaries() throws Exception {
    Path foreignBookPath = tempDirectory.resolve("foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", initializedException.getMessage());

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", accountException.getMessage());
    }

    Path unsupportedBookPath = tempDirectory.resolve("unsupported-version.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertTrue(initializedException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(openException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(accountException.getMessage().contains("format version 2 is unsupported"));
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
  void findAccount_returnsDeclaredAccountFromInitializedBook() {
    Path databasePath = tempDirectory.resolve("find-account.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      assertEquals(
          Optional.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.findAccount(new AccountCode("1000")));
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
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);

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
  void rekeyBook_rotatesPassphraseAndPreservesReadableState() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-book.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement store passphrase", "rotated-store-key".toCharArray())) {
        assertEquals(
            new RekeyBookResult.Rekeyed(bookPath.toAbsolutePath().normalize()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
    }

    try (SqlitePostingFactStore oldKeyStore = new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, oldKeyStore::listAccounts);
      assertInvalidPlaintextBookFailure(exception);
    }

    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement store passphrase", "rotated-store-key".toCharArray());
        SqlitePostingFactStore rotatedStore =
            new SqlitePostingFactStore(
                bookPath, replacementPassphrase, SqlitePostingFactStore.AccessMode.READ_ONLY)) {
      assertEquals(
          List.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z")),
              new DeclaredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          rotatedStore.listAccounts());
      assertEquals(1, queryInt(storeDatabase(rotatedStore), "pragma query_only"));
    }
  }

  @Test
  void rekeyBook_rejectsUninitializedBooksAndReadOnlyMutation() throws Exception {
    Path missingBookPath = tempDirectory.resolve("rekey-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement missing book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
      assertArrayEquals(
          new byte["rotated-store-key".getBytes(StandardCharsets.UTF_8).length],
          passphraseBytes(replacementPassphrase));
    }

    Path blankBookPath = tempDirectory.resolve("rekey-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement blank book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
      assertArrayEquals(
          new byte["rotated-store-key".getBytes(StandardCharsets.UTF_8).length],
          passphraseBytes(replacementPassphrase));
    }

    Path initializedBookPath = tempDirectory.resolve("rekey-read-only.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "read-only book passphrase", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                initializedBookPath, bookPassphrase, SqlitePostingFactStore.AccessMode.READ_ONLY)) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement read-only book", "rotated-store-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.rekeyBook(replacementPassphrase));
        assertEquals(
            "This FinGrind SQLite session is read-only and cannot mutate the book.",
            exception.getMessage());
      }
    }
  }

  @Test
  void accessModes_enforceWritableBoundariesAndQueryOnlyPolicy() throws Exception {
    assertEquals(1, SqlitePostingFactStore.AccessMode.READ_ONLY.queryOnlyPragmaValue());
    assertEquals(0, SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING.queryOnlyPragmaValue());
    assertEquals(0, SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE.queryOnlyPragmaValue());

    assertThrows(
        IllegalStateException.class,
        SqlitePostingFactStore.AccessMode.READ_ONLY::requireWritableMutation);
    assertDoesNotThrow(
        SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING::requireWritableMutation);
    assertDoesNotThrow(
        SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE::requireWritableMutation);

    assertThrows(
        IllegalStateException.class,
        SqlitePostingFactStore.AccessMode.READ_ONLY::requireWritableInitialization);
    assertThrows(
        IllegalStateException.class,
        SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING::requireWritableInitialization);
    assertDoesNotThrow(
        SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE::requireWritableInitialization);

    Path existingBookPath = tempDirectory.resolve("read-write-existing.sqlite");
    initializeBookOnDisk(existingBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                existingBookPath,
                bookPassphrase,
                SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING)) {
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
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    Path missingBookPath = tempDirectory.resolve("read-write-existing-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode missing", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                missingBookPath,
                bookPassphrase,
                SqlitePostingFactStore.AccessMode.READ_WRITE_EXISTING)) {
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
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void helperBoundaries_rejectUnsafeShapesAndWrapNativeFailures() throws Exception {
    Method querySingleInt =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "querySingleInt", SqliteNativeDatabase.class, String.class);
    querySingleInt.setAccessible(true);
    Method queryOptionalInt =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "queryOptionalInt", SqliteNativeDatabase.class, String.class);
    queryOptionalInt.setAccessible(true);
    Method bookState =
        SqlitePostingFactStore.class.getDeclaredMethod("bookState", SqliteNativeDatabase.class);
    bookState.setAccessible(true);
    Method requireInitializedBook =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "requireInitializedBook", SqliteNativeDatabase.class);
    requireInitializedBook.setAccessible(true);
    Method ordinaryOutcomeBeforeInsert =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "ordinaryOutcomeBeforeInsert", SqliteNativeDatabase.class, PostingFact.class);
    ordinaryOutcomeBeforeInsert.setAccessible(true);
    Method findOnePosting =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "findOnePosting",
            SqliteNativeDatabase.class,
            String.class,
            Class.forName("dev.erst.fingrind.sqlite.SqlitePostingFactStore$SqliteBinder"));
    findOnePosting.setAccessible(true);
    Class<?> binderType =
        Class.forName("dev.erst.fingrind.sqlite.SqlitePostingFactStore$SqliteBinder");
    ClassLoader proxyClassLoader =
        Optional.ofNullable(Thread.currentThread().getContextClassLoader())
            .orElseGet(SqlitePostingFactStoreTest.class::getClassLoader);
    Object binder =
        Proxy.newProxyInstance(
            proxyClassLoader, new Class<?>[] {binderType}, (proxy, method, arguments) -> null);

    Path blankBookPath = tempDirectory.resolve("helper-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    withStandaloneDatabase(
        bookAccess(blankBookPath),
        database -> {
          InvocationTargetException emptyQueryException =
              assertThrows(
                  InvocationTargetException.class,
                  () -> querySingleInt.invoke(null, database, "select 1 where 0"));
          assertEquals(
              "SQLite integer query returned no rows: select 1 where 0",
              emptyQueryException.getCause().getMessage());

          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(blankBookPath))) {
            InvocationTargetException blankException =
                assertThrows(
                    InvocationTargetException.class,
                    () -> requireInitializedBook.invoke(postingFactStore, database));
            assertEquals(
                "The selected SQLite file is not initialized as a FinGrind book.",
                blankException.getCause().getMessage());
          }
        });

    Path initializedBookPath = tempDirectory.resolve("helper-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          InvocationTargetException multiRowException =
              assertThrows(
                  InvocationTargetException.class,
                  () -> queryOptionalInt.invoke(null, database, "select 1 union all select 2"));
          assertEquals(
              "SQLite integer query returned more than one row: select 1 union all select 2",
              multiRowException.getCause().getMessage());
          try {
            assertEquals(OptionalInt.of(1), queryOptionalInt.invoke(null, database, "select 1"));
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(initializedBookPath))) {
            try {
              assertEquals(
                  "INITIALIZED_FINGRIND", bookState.invoke(postingFactStore, database).toString());
            } catch (ReflectiveOperationException exception) {
              throw new LinkageError(exception.getMessage(), exception);
            }
          }
        });

    Path foreignBookPath = tempDirectory.resolve("helper-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    withStandaloneDatabase(
        bookAccess(foreignBookPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
            InvocationTargetException foreignException =
                assertThrows(
                    InvocationTargetException.class,
                    () -> requireInitializedBook.invoke(postingFactStore, database));
            assertEquals(
                "The selected SQLite file is not a FinGrind book.",
                foreignException.getCause().getMessage());
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
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
            try {
              assertEquals(
                  "UNSUPPORTED_FINGRIND_VERSION",
                  bookState.invoke(postingFactStore, database).toString());
            } catch (ReflectiveOperationException exception) {
              throw new LinkageError(exception.getMessage(), exception);
            }
          }
        });

    Path incompleteBookPath = tempDirectory.resolve("helper-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    withStandaloneDatabase(
        bookAccess(incompleteBookPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(incompleteBookPath))) {
            try {
              assertEquals(
                  "INCOMPLETE_FINGRIND", bookState.invoke(postingFactStore, database).toString());
            } catch (ReflectiveOperationException exception) {
              throw new LinkageError(exception.getMessage(), exception);
            }
          }
        });

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeLibrary.open(bookAccess(blankBookPath)));
      assertEquals(
          Optional.of(new PostingCommitResult.BookNotInitialized()),
          ordinaryOutcomeBeforeInsert.invoke(
              postingFactStore,
              storeDatabase(postingFactStore),
              postingFact("posting-helper", "idem-helper", Optional.empty(), Optional.empty())));
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("find-one-stale.sqlite")))) {
      InvocationTargetException failure =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  findOnePosting.invoke(
                      postingFactStore,
                      staleDatabaseHandle(tempDirectory.resolve("find-one-stale.sqlite")),
                      SqlitePostingSql.FIND_POSTING_BY_ID,
                      binder));
      assertTrue(failure.getCause() instanceof IllegalStateException);
      assertTrue(failure.getCause().getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  void rekeyBook_clearsCachedDatabaseHandleWhenReopenFailsAfterRotation() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-reopen-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      MethodHandle originalOpenHandle = sqliteApiMethodHandle("sqlite3OpenV2");
      try {
        replaceSqliteApiMethodHandle(
            "sqlite3OpenV2",
            constantMethodHandle(
                14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class));

        try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey reopen failure", "rotated-store-key".toCharArray())) {
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> postingFactStore.rekeyBook(replacementPassphrase));

          assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
          assertNull(storeDatabase(postingFactStore));
        }
      } finally {
        replaceSqliteApiMethodHandle("sqlite3OpenV2", originalOpenHandle);
      }
    }
  }

  @Test
  void rekeyBook_preservesOpenDatabaseWhenNativeRekeyFailsBeforeClose() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      MethodHandle originalRekeyHandle = sqliteApiMethodHandle("sqlite3Rekey");
      try {
        replaceSqliteApiMethodHandle(
            "sqlite3Rekey",
            constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class));

        try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey native failure", "rotated-store-key".toCharArray())) {
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> postingFactStore.rekeyBook(replacementPassphrase));

          assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
          assertNotNull(storeDatabase(postingFactStore));
        }
      } finally {
        replaceSqliteApiMethodHandle("sqlite3Rekey", originalRekeyHandle);
      }
    }
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void assertOpenConfiguration_rejectsHardeningDrift() throws Exception {
    Method assertOpenConfiguration =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "assertOpenConfiguration",
            SqliteNativeDatabase.class,
            SqlitePostingFactStore.AccessMode.class);
    assertOpenConfiguration.setAccessible(true);

    assertOpenConfigurationFailure(
        assertOpenConfiguration,
        "pragma foreign_keys = off",
        "SQLite connection failed to keep foreign_keys enabled.");
    assertOpenConfigurationFailure(
        assertOpenConfiguration,
        "pragma trusted_schema = on",
        "SQLite connection failed to disable trusted_schema.");
    assertOpenConfigurationFailure(
        assertOpenConfiguration,
        "pragma secure_delete = off",
        "SQLite connection failed to enable secure_delete.");
    assertOpenConfigurationFailure(
        assertOpenConfiguration,
        "pragma temp_store = file",
        "SQLite connection failed to force temp_store=MEMORY.");
    assertOpenConfigurationFailure(
        assertOpenConfiguration,
        "pragma query_only = on",
        "SQLite connection failed to enforce the expected query_only setting.");
  }

  @Test
  void requireOptionalPragmaValue_enforcesPresentUnexpectedValuesOnly() {
    assertDoesNotThrow(
        () ->
            SqlitePostingFactStore.requireOptionalPragmaValue(
                OptionalInt.empty(), 1, "should stay optional"));
    assertDoesNotThrow(
        () ->
            SqlitePostingFactStore.requireOptionalPragmaValue(
                OptionalInt.of(1), 1, "should accept expected value"));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqlitePostingFactStore.requireOptionalPragmaValue(
                    OptionalInt.of(0),
                    1,
                    "SQLite connection failed to enable memory_security=fill."));

    assertEquals(
        "SQLite connection failed to enable memory_security=fill.", exception.getMessage());
  }

  @Test
  @SuppressWarnings({"PMD.AvoidAccessibilityAlteration", "PMD.UnitTestShouldIncludeAssert"})
  void bookStateHelpers_coverCanonicalAndMarkerShortCircuits() throws Exception {
    Method hasCanonicalTables =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "hasCanonicalTables", SqliteNativeDatabase.class);
    hasCanonicalTables.setAccessible(true);
    Method hasInitializedMarker =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "hasInitializedMarker", SqliteNativeDatabase.class);
    hasInitializedMarker.setAccessible(true);
    Method bookState =
        SqlitePostingFactStore.class.getDeclaredMethod("bookState", SqliteNativeDatabase.class);
    bookState.setAccessible(true);
    Method requireInitializedBook =
        SqlitePostingFactStore.class.getDeclaredMethod(
            "requireInitializedBook", SqliteNativeDatabase.class);
    requireInitializedBook.setAccessible(true);

    Path noMetaPath = tempDirectory.resolve("fgrd-no-meta.sqlite");
    createPartialFinGrindBook(noMetaPath, false, false, false, false, false);
    withStandaloneDatabase(
        bookAccess(noMetaPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(noMetaPath))) {
            assertEquals(false, hasCanonicalTables.invoke(postingFactStore, database));
            assertEquals(false, hasInitializedMarker.invoke(postingFactStore, database));
            assertEquals(
                "INCOMPLETE_FINGRIND", bookState.invoke(postingFactStore, database).toString());
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });

    Path noAccountPath = tempDirectory.resolve("fgrd-no-account.sqlite");
    createPartialFinGrindBook(noAccountPath, true, false, false, false, false);
    withStandaloneDatabase(
        bookAccess(noAccountPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(noAccountPath))) {
            assertEquals(false, hasCanonicalTables.invoke(postingFactStore, database));
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });

    Path noPostingPath = tempDirectory.resolve("fgrd-no-posting.sqlite");
    createPartialFinGrindBook(noPostingPath, true, true, false, false, false);
    withStandaloneDatabase(
        bookAccess(noPostingPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(noPostingPath))) {
            assertEquals(false, hasCanonicalTables.invoke(postingFactStore, database));
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });

    Path noJournalLinePath = tempDirectory.resolve("fgrd-no-journal-line.sqlite");
    createPartialFinGrindBook(noJournalLinePath, true, true, true, false, false);
    withStandaloneDatabase(
        bookAccess(noJournalLinePath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(noJournalLinePath))) {
            assertEquals(false, hasCanonicalTables.invoke(postingFactStore, database));
            assertEquals(
                "INCOMPLETE_FINGRIND", bookState.invoke(postingFactStore, database).toString());
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });

    Path initializedPath = tempDirectory.resolve("fgrd-initialized-short-circuit.sqlite");
    initializeBookOnDisk(initializedPath);
    withStandaloneDatabase(
        bookAccess(initializedPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(initializedPath))) {
            assertEquals(true, hasCanonicalTables.invoke(postingFactStore, database));
            assertEquals(true, hasInitializedMarker.invoke(postingFactStore, database));
            assertDoesNotThrow(() -> requireInitializedBook.invoke(postingFactStore, database));
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });

    Path versionOnlyPath = tempDirectory.resolve("foreign-version-only.sqlite");
    withStandaloneDatabase(
        bookAccess(versionOnlyPath),
        database -> database.executeStatement("pragma user_version = 1"));
    withStandaloneDatabase(
        bookAccess(versionOnlyPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(versionOnlyPath))) {
            assertEquals("FOREIGN_SQLITE", bookState.invoke(postingFactStore, database).toString());
          } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
          }
        });
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
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);
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
                    staleDatabaseHandle(tempDirectory.resolve("stale.sqlite")),
                    SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE));

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
  void findByIdempotency_rejectsForeignSqliteFileWithPostingLikeSchema()
      throws SqliteNativeException {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findByIdempotency(new IdempotencyKey("idem-partial")));

      assertEquals("The selected SQLite file is not a FinGrind book.", exception.getMessage());
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

  private static void createPartialFinGrindBook(
      Path bookPath,
      boolean includeBookMeta,
      boolean includeAccount,
      boolean includePostingFact,
      boolean includeJournalLine,
      boolean includeInitializedMarker)
      throws SqliteNativeException {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement(
              "pragma application_id = " + SqlitePostingFactStore.BOOK_APPLICATION_ID);
          database.executeStatement(
              "pragma user_version = " + SqlitePostingFactStore.BOOK_FORMAT_VERSION);
          if (includeBookMeta) {
            database.executeStatement(
                "create table book_meta (key text primary key, value text not null)");
          }
          if (includeAccount) {
            database.executeStatement(
                """
                create table account (
                    account_code text primary key,
                    account_name text not null,
                    normal_balance text not null,
                    active integer not null,
                    declared_at text not null
                )
                """);
          }
          if (includePostingFact) {
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
          }
          if (includeJournalLine) {
            database.executeStatement(
                """
                create table journal_line (
                    posting_id text not null,
                    line_order integer not null,
                    account_code text not null,
                    entry_side text not null,
                    currency_code text not null,
                    amount text not null
                )
                """);
          }
          if (includeInitializedMarker) {
            insertInitializedAtRow(database);
          }
        });
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

  private static MethodHandle sqliteApiMethodHandle(String fieldName)
      throws ReflectiveOperationException {
    Field field = sqliteApiField(fieldName);
    return (MethodHandle) field.get(sqliteApiSingleton());
  }

  private static void replaceSqliteApiMethodHandle(String fieldName, MethodHandle handle)
      throws ReflectiveOperationException {
    Field field = sqliteApiField(fieldName);
    field.set(sqliteApiSingleton(), handle);
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Field sqliteApiField(String fieldName) throws ReflectiveOperationException {
    Class<?> sqliteApiClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApi");
    Field field = sqliteApiClass.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static Object sqliteApiSingleton() throws ReflectiveOperationException {
    Class<?> sqliteApiHolderClass =
        Class.forName("dev.erst.fingrind.sqlite.SqliteNativeLibrary$SqliteApiHolder");
    Field instanceField = sqliteApiHolderClass.getDeclaredField("INSTANCE");
    instanceField.setAccessible(true);
    return instanceField.get(null);
  }

  private static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(constantType(value), value), 0, parameterTypes);
  }

  private static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case Long _ -> long.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }

  private void assertOpenConfigurationFailure(
      Method assertOpenConfiguration, String driftSql, String expectedMessage)
      throws ReflectiveOperationException, SqliteNativeException {
    Path bookPath =
        tempDirectory.resolve(expectedMessage.replace(' ', '-').replace('.', '_') + ".sqlite");
    SqliteNativeDatabase database =
        SqlitePostingFactStore.configureOpenedDatabase(
            SqliteNativeLibrary.open(bookAccess(bookPath)),
            SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE);
    try {
      database.executeScript(driftSql + ";");

      InvocationTargetException exception =
          assertThrows(
              InvocationTargetException.class,
              () ->
                  assertOpenConfiguration.invoke(
                      null, database, SqlitePostingFactStore.AccessMode.READ_WRITE_CREATE));

      assertEquals(expectedMessage, exception.getCause().getMessage());
    } finally {
      database.close();
    }
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
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static BookAccess staticBookAccess(Path bookPath) {
    try {
      Path keyPath = Files.createTempFile("fingrind-book-key-", ".key");
      keyPath.toFile().deleteOnExit();
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void assertInvalidPlaintextBookFailure(IllegalStateException exception) {
    assertTrue(exception.getMessage().contains("Failed to open SQLite book connection."));
    assertTrue(exception.getMessage().contains("SQLITE_NOTADB"));
  }

  private static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        keyPath, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }

  /** Performs one checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  private interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database) throws SqliteNativeException;
  }
}
