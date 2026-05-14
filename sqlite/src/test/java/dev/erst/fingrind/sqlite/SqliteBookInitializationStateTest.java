package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookInitializationStateTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void storeOperations_rejectDirectReadsForMissingAndRawUninitializedSqliteBooks() {
    Path missingBookPath = tempDirectory.resolve("missing-ops.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
      assertInitializedQueryViewFailure(
          () -> postingFactStore.findAccount(new AccountCode("1000")),
          () -> postingFactStore.listAccounts(firstAccountPage()),
          () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          () -> postingFactStore.findPosting(new PostingId("posting-1")),
          () -> postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new BookkeepingPostingRejection.BookNotInitialized()),
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertFalse(Files.exists(missingBookPath));
    }
    Path rawSqlitePath = tempDirectory.resolve("raw-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(rawSqlitePath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
      assertInitializedQueryViewFailure(
          () -> postingFactStore.findAccount(new AccountCode("1000")),
          () -> postingFactStore.listAccounts(firstAccountPage()),
          () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          () -> postingFactStore.findPosting(new PostingId("posting-1")),
          () -> postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash"),
              dev.erst.fingrind.core.AccountType.ASSET,
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new BookkeepingPostingRejection.BookNotInitialized()),
          commitPosting(
              postingFactStore,
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
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  declareAccount(
                      postingFactStore,
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      dev.erst.fingrind.core.AccountType.ASSET,
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void openBook_rejectsAlreadyInitializedBook() {
    Path databasePath = tempDirectory.resolve("already-initialized.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertEquals(
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookAlreadyInitialized()),
          postingFactStore.openBook(Instant.parse("2026-04-08T10:15:30Z"), bookIdentity()));
    }
  }

  @Test
  void openBook_initializesBlankSqliteFile() {
    Path databasePath = tempDirectory.resolve("blank-before-open.sqlite");
    createEmptySqliteFile(databasePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void schemaOnlyBook_isRejectedAsIncompleteFinGrindBook() {
    Path databasePath = tempDirectory.resolve("schema-only.sqlite");
    createSchemaOnlyBook(databasePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.INCOMPLETE_FINGRIND,
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(
          NullTestSupport.messageOf(accountException)
              .contains("incomplete or corrupted and cannot be opened safely"));
      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(
          NullTestSupport.messageOf(openException)
              .contains("incomplete or corrupted and cannot be opened safely"));
    }
  }

  @Test
  void inspectBook_reportsLifecycleAndCompatibilityStates() throws Exception {
    Path missingBookPath = tempDirectory.resolve("inspect-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Missing(SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
    Path blankBookPath = tempDirectory.resolve("inspect-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
    Path initializedBookPath = tempDirectory.resolve("inspect-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedBookPath))) {
      assertEquals(
          initializedLifecycleInspection(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION,
              Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.inspectBook());
    }
    Path foreignBookPath = tempDirectory.resolve("inspect-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.FOREIGN_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
    Path unsupportedBookPath = tempDirectory.resolve("inspect-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    int unsupportedVersion = SqliteBookContract.FORMAT_VERSION + 1;
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = " + unsupportedVersion));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              SqliteBookContract.APPLICATION_ID,
              unsupportedVersion,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
    Path incompleteBookPath = tempDirectory.resolve("inspect-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(incompleteBookPath))) {
      assertEquals(
          new BookLifecycleInspection.Existing(
              BookLifecycleInspection.Status.INCOMPLETE_FINGRIND,
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION),
          postingFactStore.inspectBook());
    }
  }

  @Test
  void inspectBook_reloadsStateAfterExplicitCacheClear() throws Exception {
    Path initializedBookPath = tempDirectory.resolve("inspect-cache-clear.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedBookPath))) {
      assertEquals(
          BookLifecycleInspection.Status.INITIALIZED, postingFactStore.inspectBook().status());
      setStoreCachedBookState(
          postingFactStore, new SqliteBookStateSnapshot(0, 0, SqliteBookState.BLANK_SQLITE));
      assertEquals(
          BookLifecycleInspection.Status.BLANK_SQLITE, postingFactStore.inspectBook().status());
      setStoreCachedBookState(postingFactStore, null);
      assertEquals(
          BookLifecycleInspection.Status.INITIALIZED, postingFactStore.inspectBook().status());
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
              () ->
                  postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to initialize SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void bookStateSnapshot_rejectsNegativeHeaderMetadata() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteBookStateSnapshot(-1, 0, SqliteBookState.FOREIGN_SQLITE));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SqliteBookStateSnapshot(0, -1, SqliteBookState.FOREIGN_SQLITE));
  }
}
