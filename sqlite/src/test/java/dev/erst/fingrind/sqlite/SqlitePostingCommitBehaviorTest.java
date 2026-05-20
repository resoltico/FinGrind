package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqlitePostingCommitBehaviorTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void commit_returnsUnknownAndInactiveAccountOutcomes() {
    Path databasePath = tempDirectory.resolve("account-rejections.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
      assertEquals(
          rejected(
              accountStateViolations(
                  new BookkeepingPostingRejection.UnknownAccount(new AccountCode("1000")),
                  new BookkeepingPostingRejection.UnknownAccount(new AccountCode("2000")))),
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      declareDefaultAccounts(postingFactStore);
      deactivateAccount(databasePath, "1000");
      assertEquals(
          rejected(
              accountStateViolations(
                  new BookkeepingPostingRejection.InactiveAccount(new AccountCode("1000")))),
          commitPosting(
              postingFactStore,
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commitAndFinders_roundTripPostingWithoutReversal() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    CommittedPosting postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(postingFact),
          commitPosting(postingFactStore, postingFact));
      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          Optional.of(postingFact), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commitAndFindByIdempotency_preservesReversalReference() {
    Path databasePath = tempDirectory.resolve("nested").resolve("entity-b.sqlite");
    CommittedPosting originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    CommittedPosting reversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, originalFact);
      commitPosting(postingFactStore, reversalFact);
      assertEquals(
          Optional.of(reversalFact),
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-2")));
    }
  }

  @Test
  void commit_returnsDuplicateIdempotencyOutcome() {
    Path databasePath = tempDirectory.resolve("fingrind.sqlite");
    CommittedPosting postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, postingFact);
      assertEquals(
          rejected(new BookkeepingPostingRejection.DuplicateIdempotencyKey()),
          commitPosting(
              postingFactStore,
              postingFact("posting-2", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commit_returnsDuplicateReversalTargetOutcome() {
    Path databasePath = tempDirectory.resolve("reversal.sqlite");
    CommittedPosting originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    CommittedPosting firstReversal =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));
    CommittedPosting secondReversal =
        postingFact(
            "posting-3",
            "idem-3",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("another full reversal")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(postingFactStore, originalFact);
      commitPosting(postingFactStore, firstReversal);
      assertEquals(
          rejected(
              new BookkeepingPostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          commitPosting(postingFactStore, secondReversal));
      assertEquals(
          Optional.of(firstReversal), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commit_throwsWhenPostingIdUniqueConstraintConflictsWithDifferentIdempotencyKey() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  commitPosting(
                      postingFactStore,
                      postingFact("posting-1", "idem-2", Optional.empty(), Optional.empty())));
      SqliteNativeException nativeFailure =
          assertInstanceOf(SqliteNativeException.class, exception.getCause());
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to commit SQLite posting fact."));
      assertEquals("SQLITE_CONSTRAINT_UNIQUE", nativeFailure.resultName());
    }
  }

  @Test
  void commit_rejectsMissingReversalTargetBeforeAnyForeignKeyWrite() {
    Path databasePath = tempDirectory.resolve("unexpected.sqlite");
    CommittedPosting invalidReversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-missing"))),
            Optional.of(new ReversalReason("operator reversal")));
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          rejected(
              new BookkeepingPostingRejection.ReversalTargetNotFound(
                  new PostingId("posting-missing"))),
          commitPosting(postingFactStore, invalidReversalFact));
    }
  }

  @Test
  void commit_rejectsBookPathWhoseParentIsAFile() throws IOException {
    Path fileParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(fileParent, "nope", StandardCharsets.UTF_8);
    Path keyPath = tempDirectory.resolve("book-keys").resolve("entity.book-key");
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
    try (SqlitePostingFactStore postingFactStore =
        openStore(
            new BookAccess(
                fileParent.resolve("entity.sqlite"),
                new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("must resolve beneath an existing directory"));
    }
  }

  @Test
  void findByIdempotency_throwsWhenExistingBookFileIsNotSqlite() throws IOException {
    Path databasePath = tempDirectory.resolve("not-a-database.sqlite");
    Files.writeString(databasePath, "not sqlite", StandardCharsets.UTF_8);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void findByIdempotency_throwsWhenBookPathPointsAtDirectory() throws IOException {
    Path databasePath = tempDirectory.resolve("book-directory");
    Files.createDirectories(databasePath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("must resolve to one regular non-symlink file"));
    }
  }

  @Test
  void commit_ignoresRollbackFailureWhenPrimaryFailureAlreadyExists() throws Exception {
    Path bookPath = tempDirectory.resolve("rollback-native-failure.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  commitPosting(
                      postingFactStore,
                      postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertTrue(
          NullTestSupport.messageOf(exception).contains("Failed to commit SQLite posting fact."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void commit_postingIdUniqueConflictWithFirstReversalLeavesUniqueConstraintAsPrimaryFailure() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id-reversal.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      commitPosting(
          postingFactStore, postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  commitPosting(
                      postingFactStore,
                      postingFact(
                          "posting-1",
                          "idem-2",
                          Optional.of(new ReversalReference(new PostingId("posting-1"))),
                          Optional.of(new ReversalReason("full reversal")))));
      SqliteNativeException nativeFailure =
          assertInstanceOf(SqliteNativeException.class, exception.getCause());
      assertEquals("SQLITE_CONSTRAINT_UNIQUE", nativeFailure.resultName());
    }
  }
}
