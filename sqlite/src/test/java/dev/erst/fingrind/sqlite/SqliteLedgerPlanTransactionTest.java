package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteLedgerPlanTransactionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void ledgerPlanTransaction_commitsOuterTransactionAndPersistsNestedMutations() {
    Path databasePath = tempDirectory.resolve("ledger-plan-commit.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      assertEquals(
          new BookOpeningOutcome.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
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
          new AccountDeclarationOutcome.Declared(
              new RegisteredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:31Z"))),
          postingFactStore.declareAccount(
              new AccountCode("2000"),
              new AccountName("Revenue"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-07T10:15:31Z")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())),
          postingFactStore.commit(
              postingDraft("posting-1", "idem-1", Optional.empty(), Optional.empty()),
              () -> new PostingId("posting-1")));
      postingFactStore.commitLedgerPlanTransaction();
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertTrue(postingFactStore.inspectBook().initialized());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(postingFactStore.findPosting(new PostingId("posting-1")).isPresent());
    }
  }

  @Test
  void ledgerPlanTransaction_rollsBackOuterTransactionAndRejectsInvalidLifecycleCalls() {
    Path databasePath = tempDirectory.resolve("ledger-plan-rollback.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertThrows(IllegalStateException.class, postingFactStore::commitLedgerPlanTransaction);
      postingFactStore.beginLedgerPlanTransaction();
      assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.rollbackLedgerPlanTransaction();
      postingFactStore.rollbackLedgerPlanTransaction();
      assertFalse(Files.exists(databasePath));
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertFalse(postingFactStore.inspectBook().initialized());
      assertInitializedQueryViewFailure(
          () -> postingFactStore.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void ledgerPlanTransaction_defersExistingHandleValidationUntilDatabaseWork() throws Exception {
    Path beginFailurePath = tempDirectory.resolve("ledger-plan-deferred-begin.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(beginFailurePath))) {
      try (SqliteNativeDatabase closedDatabase =
          SqliteNativeConnections.open(bookAccess(beginFailurePath))) {
        closedDatabase.close();
        setStoreDatabase(postingFactStore, closedDatabase);
      }
      assertDoesNotThrow(postingFactStore::beginLedgerPlanTransaction);
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
    Path commitFailurePath = tempDirectory.resolve("ledger-plan-commit-failure.sqlite");
    initializeBookOnDisk(commitFailurePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(commitFailurePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      closeStoreDatabase(postingFactStore);
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::commitLedgerPlanTransaction);
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("Failed to commit SQLite ledger plan transaction."));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }

  @Test
  void ledgerPlanTransaction_wrapsNativeCommitFailureWhenTransactionEndsExternally() {
    Path databasePath = tempDirectory.resolve("ledger-plan-native-commit-failure.sqlite");
    AccountCode deferredAccount = new AccountCode("3000");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.declareAccount(
          deferredAccount,
          new AccountName("Deferred Revenue"),
          NormalBalance.CREDIT,
          Instant.parse("2026-04-07T10:15:32Z"));
      requireStoreDatabase(postingFactStore).executeStatement("rollback");
      SqliteStorageFailureException exception =
          assertThrows(
              SqliteStorageFailureException.class, postingFactStore::commitLedgerPlanTransaction);
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains("Failed to commit SQLite ledger plan transaction."));
      assertInstanceOf(SqliteNativeException.class, exception.getCause());
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(Optional.empty(), postingFactStore.findAccount(deferredAccount));
    }
  }

  @Test
  void findAccounts_returnsEmptyForMissingAndBlankBooksAndDeclaredRowsForInitializedBooks() {
    AccountCode cash = new AccountCode("1000");
    AccountCode revenue = new AccountCode("2000");
    Set<AccountCode> requestedAccounts = Set.of(cash, revenue);
    Path missingPath = tempDirectory.resolve("find-accounts-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingPath))) {
      assertEquals(Map.of(), postingFactStore.findAccounts(Set.of()));
      assertInitializedQueryViewFailure(() -> postingFactStore.findAccounts(requestedAccounts));
    }
    Path blankPath = tempDirectory.resolve("find-accounts-blank.sqlite");
    createEmptySqliteFile(blankPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankPath))) {
      assertInitializedQueryViewFailure(() -> postingFactStore.findAccounts(requestedAccounts));
    }
    Path initializedPath = tempDirectory.resolve("find-accounts-initialized.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          Map.of(
              cash,
              postingFactStore.findAccount(cash).orElseThrow(),
              revenue,
              postingFactStore.findAccount(revenue).orElseThrow()),
          postingFactStore.findAccounts(requestedAccounts));
    }
  }

  @Test
  void ledgerPlanTransaction_preservesMissingBookStateUntilPlanMutation() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "plan execution missing book", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertNull(storeDatabase(postingFactStore));
      postingFactStore.commitLedgerPlanTransaction();
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void deferredMissingBookTransaction_remainsUnbegunAndClosesWithoutArtifacts() throws Exception {
    Path databasePath =
        tempDirectory.resolve("deferred-plan-close").resolve("nested").resolve("book.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "deferred plan close", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertNull(storeDatabase(postingFactStore));
      assertFalse(Files.exists(databasePath));
      assertFalse(Files.exists(databasePath.getParent()));
    }
    assertFalse(Files.exists(databasePath));
    assertFalse(Files.exists(databasePath.getParent()));
  }

  @Test
  void ledgerPlanTransaction_rollbackRemovesCreatedBookArtifactsForPlanExecutionMode()
      throws Exception {
    Path parentDirectory = tempDirectory.resolve("rolled-back-plan").resolve("nested");
    Path databasePath = parentDirectory.resolve("ledger-plan-created.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "plan execution rollback cleanup", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.rollbackLedgerPlanTransaction();
      assertFalse(Files.exists(databasePath));
      assertFalse(Files.exists(parentDirectory));
      assertFalse(Files.exists(parentDirectory.getParent()));
    }
  }

  @Test
  void ledgerPlanTransaction_closeRemovesCreatedBookArtifactsWhenSessionEndsMidPlan()
      throws Exception {
    Path parentDirectory = tempDirectory.resolve("abandoned-plan").resolve("nested");
    Path databasePath = parentDirectory.resolve("ledger-plan-close-cleanup.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    }
    assertFalse(Files.exists(databasePath));
    assertFalse(Files.exists(parentDirectory));
    assertFalse(Files.exists(parentDirectory.getParent()));
  }

  @Test
  void ledgerPlanTransaction_beginFailureCleansCreatedDirectoriesWhenOpeningMissingBookFailsEarly()
      throws Exception {
    Path parentDirectory = tempDirectory.resolve("begin-failure").resolve("nested");
    Path databasePath = parentDirectory.resolve("failure.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "begin failure cleanup", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      clearStoreSessionSecret(postingFactStore);
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);
      assertEquals("SQLite book session secret is no longer available.", exception.getMessage());
      assertFalse(Files.exists(databasePath));
      assertFalse(Files.exists(parentDirectory));
      assertFalse(Files.exists(parentDirectory.getParent()));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
  }

  @Test
  void ledgerPlanTransaction_beginFailureSuppressesCleanupFailureWhenDirectoryRemovalAlsoFails()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"));
        SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "begin failure suppressed cleanup", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                fileSystem.path("\\begin-failure\\suppressed.sqlite"),
                bookPassphrase,
                SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      fileSystem
          .path("\\begin-failure")
          .failDeleteIfExistsWith(new AccessDeniedException("\\begin-failure"));
      clearStoreSessionSecret(postingFactStore);
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);
      assertEquals("SQLite book session secret is no longer available.", exception.getMessage());
      assertEquals(1, exception.getSuppressed().length);
      assertTrue(
          NullTestSupport.messageOf(exception.getSuppressed()[0])
              .contains(
                  "Failed to remove an empty SQLite book directory created during rolled-back plan cleanup"));
    }
  }

  @Test
  void ledgerPlanTransaction_rollbackKeepsCreatedParentDirectoryWhenSiblingFileMakesItNonEmpty()
      throws Exception {
    Path parentDirectory = tempDirectory.resolve("non-empty-cleanup").resolve("nested");
    Path databasePath = parentDirectory.resolve("rollback.sqlite");
    Path siblingFile = parentDirectory.resolve("keep.txt");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rollback cleanup sibling", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      Files.writeString(siblingFile, "preserve parent");
      postingFactStore.rollbackLedgerPlanTransaction();
      assertFalse(Files.exists(databasePath));
      assertTrue(Files.exists(siblingFile));
      assertTrue(Files.exists(parentDirectory));
    }
  }

  @Test
  void ledgerPlanTransaction_rollbackWrapsCleanupCloseFailureForCreatedMissingBook()
      throws Exception {
    Path databasePath = tempDirectory.resolve("rollback-cleanup-close-failure.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rollback cleanup close failure", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      try (SqliteNativeDatabase openedDatabase = requireStoreDatabase(postingFactStore)) {
        assertNotNull(openedDatabase);
        setStoreDatabase(postingFactStore, new IllegalStateClosingSqliteNativeDatabase());
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class, postingFactStore::rollbackLedgerPlanTransaction);
        assertEquals(
            "Failed to close the SQLite book created during rolled-back plan cleanup.",
            exception.getMessage());
        assertNull(storeDatabase(postingFactStore));
      }
    }
  }

  @Test
  void ledgerPlanTransaction_closeWithoutCreatedArtifactsLeavesMissingBookMissing()
      throws Exception {
    Path databasePath = tempDirectory.resolve("close-without-created-artifacts.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "close without created artifacts", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.close();
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void lifecycleCleanupHelper_returnsImmediatelyWhenNothingWasMarked() {
    assertDoesNotThrow(
        () ->
            SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
                tempDirectory.resolve("no-op-cleanup.sqlite"), tempDirectory, null));
  }

  @Test
  void lifecycleCleanupHelper_ignoresMissingDirectories() {
    Path missingDirectory = tempDirectory.resolve("missing-parent-chain").resolve("nested");
    assertDoesNotThrow(
        () ->
            SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
                missingDirectory, tempDirectory));
  }

  @Test
  void lifecycleCleanupHelper_treatsDeleteRaceAsAlreadyGone() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath createdDirectory =
          fileSystem
              .path("\\created\\nested")
              .failDeleteIfExistsWith(new NoSuchFileException("\\created\\nested"));
      assertDoesNotThrow(
          () ->
              SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
                  createdDirectory, fileSystem.path("\\created")));
    }
  }

  @Test
  void lifecycleCleanupHelper_wrapsDirectoryDeletionFailuresFromCustomFilesystem() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath createdDirectory =
          fileSystem
              .path("\\created\\nested")
              .failDeleteIfExistsWith(new AccessDeniedException("\\created\\nested"));
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
                      createdDirectory, fileSystem.path("\\created")));
      assertTrue(
          NullTestSupport.messageOf(exception)
              .contains(
                  "Failed to remove an empty SQLite book directory created during rolled-back plan cleanup"));
    }
  }

  @Test
  void lifecycleCleanupHelper_skipsWhenStartingDirectoryAlreadyMatchesBoundary() {
    assertDoesNotThrow(
        () ->
            SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
                tempDirectory, tempDirectory));
  }

  @Test
  void lifecycleCleanupHelper_stopsWhenParentChainRunsOut() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      assertDoesNotThrow(
          () ->
              SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
                  fileSystem.path("\\"), fileSystem.path("\\created")));
    }
  }

  @Test
  void lifecycleCleanupHelper_wrapsArtifactDeletionFailures() throws Exception {
    Path blockingDirectory = tempDirectory.resolve("artifact-directory");
    Files.createDirectories(blockingDirectory);
    Files.writeString(blockingDirectory.resolve("nested.txt"), "not empty");
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteLedgerPlanArtifactCleanup.deleteBookArtifactIfPresent(blockingDirectory));
    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains(
                "Failed to remove SQLite book artifact created during rolled-back plan cleanup"));
  }

  @Test
  void lifecycleCleanupHelper_returnsNullAncestorForFilesystemRoot() {
    assertNull(
        SqliteLedgerPlanArtifactCleanup.nearestExistingAncestor(
            tempDirectory.toAbsolutePath().getRoot()));
  }

  @Test
  void ledgerPlanTransaction_opensExistingBookImmediatelyInPlanExecutionMode() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-existing.sqlite");
    initializeBookOnDisk(databasePath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "plan execution existing book", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertNotNull(storeDatabase(postingFactStore));
      postingFactStore.rollbackLedgerPlanTransaction();
    }
  }

  @Test
  void ledgerPlanTransaction_resetsActivationFlagsWhenBeginCannotOpenDatabase() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-begin-passphrase-missing.sqlite");
    initializeBookOnDisk(databasePath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "begin failure missing passphrase", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      clearStoreSessionSecret(postingFactStore);
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);
      assertEquals("SQLite book session secret is no longer available.", exception.getMessage());
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
  }

  @Test
  void ledgerPlanTransaction_rollbackToleratesClosedStoreWithActiveOuterTransaction() {
    Path databasePath = tempDirectory.resolve("ledger-plan-closed-rollback.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.close();
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }
}
