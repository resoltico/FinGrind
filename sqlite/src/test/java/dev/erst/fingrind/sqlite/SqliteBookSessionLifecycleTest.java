package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import java.nio.file.Path;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookSessionLifecycleTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void lifecyclePrime_coversPublishedDatabaseAndAuthenticationRejectionBranches() throws Exception {
    Path publishedPath = tempDirectory.resolve("prime-published.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(publishedPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeConnections.open(bookAccess(publishedPath)));
      assertDoesNotThrow(() -> postingFactStore.prime().requireAccepted());
    }

    Path existingPath = tempDirectory.resolve("prime-existing.sqlite");
    initializeBookOnDisk(existingPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(existingPath))) {
      assertDoesNotThrow(() -> postingFactStore.prime().requireAccepted());
    }

    Path existingReadWriteExistingPath =
        tempDirectory.resolve("prime-existing-read-write-existing.sqlite");
    initializeBookOnDisk(existingReadWriteExistingPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
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
                ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.code(), failure.code());
      }
    }
  }

  @Test
  void lifecycleTransactionBranches_coverExistingCreateAndDetachedRollbackPaths() throws Exception {
    Path existingPath = tempDirectory.resolve("lifecycle-existing-create.sqlite");
    initializeBookOnDisk(existingPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(existingPath))) {
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
        new SqlitePostingFactStore(
            bookAccess(existingPath), SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }
}
