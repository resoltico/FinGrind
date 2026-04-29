package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingCommitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteLedgerPlanTransactionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void ledgerPlanTransaction_commitsOuterTransactionAndPersistsNestedMutations() {
    Path databasePath = tempDirectory.resolve("ledger-plan-commit.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();

      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
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
      assertTrue(postingFactStore.isInitialized());
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
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertFalse(postingFactStore.isInitialized());
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
          exception.getMessage().contains("Failed to commit SQLite ledger plan transaction."));
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
          exception.getMessage().contains("Failed to commit SQLite ledger plan transaction."));
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
      setStoreBookPassphrase(postingFactStore, null);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);

      assertEquals("SQLite book passphrase is no longer available.", exception.getMessage());
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
