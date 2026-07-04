package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookSessionLifecycleTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void lifecyclePrime_coversPublishedDatabaseAndAuthenticationRejectionBranches() throws Exception {
    Path publishedPath = tempDirectory.resolve("prime-published.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(publishedPath))) {
      setStoreDatabase(postingFactStore, openNativeDatabase(bookAccess(publishedPath)));
      assertDoesNotThrow(() -> postingFactStore.prime().requireAccepted());
    }
    Path existingPath = tempDirectory.resolve("prime-existing.sqlite");
    initializeBookOnDisk(existingPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(existingPath))) {
      assertDoesNotThrow(() -> postingFactStore.prime().requireAccepted());
    }
    Path existingReadWriteExistingPath =
        tempDirectory.resolve("prime-existing-read-write-existing.sqlite");
    initializeBookOnDisk(existingReadWriteExistingPath);
    try (SqlitePostingFactStore postingFactStore =
        openStore(
            bookAccess(existingReadWriteExistingPath), SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertDoesNotThrow(() -> postingFactStore.prime().requireAccepted());
    }
    Path initializedPath = tempDirectory.resolve("prime-wrong-passphrase.sqlite");
    initializeBookOnDisk(initializedPath);
    try (SqliteBookPassphrase wrongPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "prime wrong passphrase", "wrong-passphrase".toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                initializedPath, wrongPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      ContractDecision<SqlitePostingFactStore> decision = postingFactStore.prime();
      switch (decision) {
        case ContractDecision.Accepted<SqlitePostingFactStore> _ ->
            throw new AssertionError("Expected lifecycle priming to be rejected.");
        case ContractDecision.Rejected<SqlitePostingFactStore>(var failure) ->
            assertEquals(
                ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
                failure.code());
      }
    }
  }

  @Test
  void lifecycleTransactionBranches_coverExistingCreateAndDetachedRollbackPaths() throws Exception {
    Path existingPath = tempDirectory.resolve("lifecycle-existing-create.sqlite");
    initializeBookOnDisk(existingPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(existingPath))) {
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      try (SqliteNativeDatabase detachedDatabase = requireStoreDatabase(postingFactStore)) {
        setStoreDatabase(postingFactStore, null);
        assertNotNull(detachedDatabase.handle());
        assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
        assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
        assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      }
    }
    try (SqlitePostingFactStore postingFactStore =
        openStore(bookAccess(existingPath), SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }

  @Test
  void lifecycleTransactionBegin_rejectsAuthenticationFailureWithoutLeavingActiveState()
      throws Exception {
    Path existingPath = tempDirectory.resolve("lifecycle-begin-wrong-passphrase.sqlite");
    initializeBookOnDisk(existingPath);
    try (SqliteBookPassphrase wrongPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "lifecycle wrong passphrase", "wrong-passphrase".toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                existingPath, wrongPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      ContractFailureException exception =
          assertThrows(
              ContractFailureException.class, postingFactStore::beginLedgerPlanTransaction);
      assertEquals(
          ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
          exception.failure().code());
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
  }

  @Test
  void lifecycleIntrospection_coversDeferredTransactionBranchStates() {
    Path databasePath =
        tempDirectory
            .resolve("lifecycle-deferred-introspection")
            .resolve("nested")
            .resolve("book.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        openStore(bookAccess(databasePath), SqliteStoreAccessMode.PLAN_EXECUTION)) {
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(
          () ->
              SqliteStoreTestAccess.invokeCleanupCreatedMissingBookArtifactsIfPresent(
                  postingFactStore));
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(
          () ->
              SqliteStoreTestAccess.invokeCleanupCreatedMissingBookArtifactsIfPresent(
                  postingFactStore));
    }
  }

  @Test
  void lifecycleIntrospection_coversCreatedArtifactCleanupBranchState() throws Exception {
    Path parentDirectory = tempDirectory.resolve("lifecycle-created-cleanup").resolve("nested");
    Path databasePath = parentDirectory.resolve("book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.openBook(
          Instant.parse("2026-04-07T10:15:30Z"),
          bookIdentity(),
          dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
              bookIdentity().bookDoctrine()));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(
          () ->
              SqliteStoreTestAccess.invokeCleanupCreatedMissingBookArtifactsIfPresent(
                  postingFactStore));
      assertFalse(Files.exists(databasePath));
      assertFalse(Files.exists(parentDirectory));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
  }
}
