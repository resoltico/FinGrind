package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookInitializationStateTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void storeOperations_handleMissingAndRawUninitializedSqliteBooks() {
    Path missingBookPath = tempDirectory.resolve("missing-ops.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(List.of(), listAccounts(postingFactStore));
      assertEquals(
          Optional.empty(), postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new PostingRejection.BookNotInitialized()),
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
      assertEquals(List.of(), listAccounts(postingFactStore));
      assertEquals(
          Optional.empty(), postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new PostingRejection.BookNotInitialized()),
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
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));

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
  void openBook_initializesBlankSqliteFile() {
    Path databasePath = tempDirectory.resolve("blank-before-open.sqlite");
    createEmptySqliteFile(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void schemaOnlyBook_isRejectedAsIncompleteFinGrindBook() {
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
  void inspectBook_reportsLifecycleAndCompatibilityStates() throws Exception {
    Path missingBookPath = tempDirectory.resolve("inspect-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertEquals(
          new BookInspection.Missing(
              SqliteBookContract.FORMAT_VERSION, BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path blankBookPath = tempDirectory.resolve("inspect-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.BLANK_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path initializedBookPath = tempDirectory.resolve("inspect-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedBookPath))) {
      assertEquals(
          new BookInspection.Initialized(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
              Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.inspectBook());
    }

    Path foreignBookPath = tempDirectory.resolve("inspect-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.FOREIGN_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path unsupportedBookPath = tempDirectory.resolve("inspect-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              SqliteBookContract.APPLICATION_ID,
              2,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path incompleteBookPath = tempDirectory.resolve("inspect-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(incompleteBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.INCOMPLETE_FINGRIND,
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }
  }

  @Test
  void openBook_wrapsInitializationFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("schema-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to initialize SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }
}
