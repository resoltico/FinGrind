package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookRekeyAndValidationTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant REKEYED_AT = Instant.parse("2026-04-08T10:15:30Z");

  @Test
  void rekeyBook_contractLevelResolverUsesNewSecretIntentAndSurfacesRejections() throws Exception {
    Path acceptedBookPath = tempDirectory.resolve("rekey-contract-level.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(acceptedBookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      BookAccess.PassphraseSource replacementSource =
          BookAccess.PassphraseSource.StandardInput.INSTANCE;
      ContractDecision<RekeyBookResult> acceptedDecision =
          postingFactStore.rekeyBook(
              replacementSource,
              (resolvedBookPath, passphraseSource, intent) -> {
                assertEquals(acceptedBookPath, resolvedBookPath);
                assertEquals(replacementSource, passphraseSource);
                assertEquals(SqlitePassphraseIntent.NEW_SECRET, intent);
                return ContractDecision.accepted(
                    SqliteBookPassphrase.fromCharacters(
                        "contract-level replacement", "rotated-contract-key".toCharArray()));
              },
              REKEYED_AT);
      assertEquals(
          new RekeyBookResult.Rekeyed(acceptedBookPath.toAbsolutePath().normalize()),
          acceptedDecision.requireAccepted());
    }
    try (SqlitePostingFactStore rejectedStore =
        openStore(bookAccess(tempDirectory.resolve("rekey-contract-rejected.sqlite")))) {
      initializeBookWithDefaultAccounts(rejectedStore);
      ContractDecision<RekeyBookResult> rejectedDecision =
          rejectedStore.rekeyBook(
              BookAccess.PassphraseSource.StandardInput.INSTANCE,
              (resolvedBookPath, passphraseSource, intent) ->
                  ContractDecision.rejected(
                      ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
                          "Rejected replacement secret", null, null)),
              REKEYED_AT);
      switch (rejectedDecision) {
        case ContractDecision.Accepted<RekeyBookResult>(RekeyBookResult result) ->
            throw new AssertionError("Expected rejected replacement secret but was " + result);
        case ContractDecision.Rejected<RekeyBookResult>(var failure) ->
            assertEquals(
                ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(), failure.code());
      }
    }
  }

  @Test
  void rekeyBook_rotatesPassphraseAndPreservesReadableState() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-book.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement store passphrase", "rotated-store-key".toCharArray())) {
        assertEquals(
            new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rekeyed(
                bookPath.toAbsolutePath().normalize()),
            postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
      }
    }
    try (SqlitePostingFactStore oldKeyStore = openStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> oldKeyStore.listAccounts(firstAccountPage()));
      assertProtectedBookVerificationFailure(exception);
    }
    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement store passphrase", "rotated-store-key".toCharArray());
        SqlitePostingFactStore rotatedStore =
            new SqlitePostingFactStore(
                bookPath, replacementPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      assertEquals(
          List.of(
              declaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z")),
              declaredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  dev.erst.fingrind.core.AccountType.REVENUE,
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          listAccounts(rotatedStore));
      assertEquals("delete", queryText(requireStoreDatabase(rotatedStore), "pragma journal_mode"));
      assertEquals(3, queryInt(requireStoreDatabase(rotatedStore), "pragma synchronous"));
      assertEquals(1, queryInt(requireStoreDatabase(rotatedStore), "pragma query_only"));
    }
  }

  @Test
  void rekeyBook_rejectsUninitializedBooksAndReadOnlyMutation() throws Exception {
    Path missingBookPath = tempDirectory.resolve("rekey-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement missing book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
      }
      assertArrayEquals(
          new byte["rotated-store-key".getBytes(StandardCharsets.UTF_8).length],
          passphraseBytes(replacementPassphrase));
    }
    Path blankBookPath = tempDirectory.resolve("rekey-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement blank book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
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
                initializedBookPath, bookPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement read-only book", "rotated-store-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
        assertEquals(
            "This FinGrind SQLite session is read-only and cannot mutate the book.",
            exception.getMessage());
      }
    }
  }

  @Test
  void readOnlySessions_preserveExistingBookPermissionsWhileWritableSessionsRepairThem()
      throws Exception {
    Path bookPath = tempDirectory.resolve("permissions-on-open.sqlite");
    initializeBookOnDisk(bookPath);
    assumeTrue(bookPath.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Set<PosixFilePermission> relaxedPermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ);
    Set<PosixFilePermission> hardenedPermissions =
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    Files.setPosixFilePermissions(bookPath, relaxedPermissions);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "read-only permissions open", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(bookPath, bookPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      listAccounts(postingFactStore);
    }
    assertEquals(relaxedPermissions, Files.getPosixFilePermissions(bookPath));
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "writable permissions open", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                bookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      listAccounts(postingFactStore);
    }
    assertEquals(hardenedPermissions, Files.getPosixFilePermissions(bookPath));
  }

  @Test
  void transactionValidationBook_wrapsNativeFailuresForStateAndAccountLookups() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(bookPath), postingFactStore.postingReader());
      IllegalStateException initializedFailure =
          assertThrows(IllegalStateException.class, validationBook::inspectBook);
      assertTrue(
          NullTestSupport.messageOf(initializedFailure).contains("Failed to query SQLite book."));
      IllegalStateException accountFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findAccount(new AccountCode("1000")));
      assertTrue(
          NullTestSupport.messageOf(accountFailure).contains("Failed to query SQLite book."));
      IllegalStateException postingFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findExistingPosting(new IdempotencyKey("idem-1")));
      assertTrue(
          NullTestSupport.messageOf(postingFailure).contains("Failed to query SQLite book."));
      IllegalStateException accountsFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  validationBook.findAccounts(
                      java.util.Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertTrue(
          NullTestSupport.messageOf(accountsFailure).contains("Failed to query SQLite book."));
      IllegalStateException postingsFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.postings(EffectiveDateRange.unbounded()));
      assertTrue(
          NullTestSupport.messageOf(postingsFailure).contains("Failed to query SQLite book."));
      IllegalStateException earliestFailure =
          assertThrows(IllegalStateException.class, validationBook::earliestPostingEffectiveDate);
      assertTrue(
          NullTestSupport.messageOf(earliestFailure).contains("Failed to query SQLite book."));
      IllegalStateException closedThroughFailure =
          assertThrows(IllegalStateException.class, validationBook::closedThroughEffectiveDate);
      assertTrue(
          NullTestSupport.messageOf(closedThroughFailure).contains("Failed to query SQLite book."));
    }
  }

  @Test
  void transactionValidationBook_findsDeclaredAccountsThroughBatchLookup() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-success.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              requireStoreDatabase(postingFactStore), postingFactStore.postingReader());
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          validationBook.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void rekeyBook_clearsCachedDatabaseHandleWhenReopenFailsAfterRotation() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-reopen-failure.sqlite");
    AtomicReference<SqliteNativeApi> sqliteApi = new AtomicReference<>(SqliteNativeBootstrap.api());
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            bookPath,
            loadPassphrase(bookAccess(bookPath)),
            SqliteStoreAccessMode.READ_WRITE_CREATE,
            sqliteApi::get)) {
      initializeBookWithDefaultAccounts(postingFactStore);
      sqliteApi.set(
          SqliteNativeApiTestSupport.withOpenV2(
              SqliteNativeBootstrap.api(),
              constantMethodHandle(
                  14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class)));
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "rekey reopen failure", "rotated-store-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
        assertTrue(
            NullTestSupport.messageOf(exception)
                .contains("FinGrind restored the pre-rekey book on disk"));
        assertNull(storeDatabase(postingFactStore));
      }
    }
    try (SqlitePostingFactStore restoredStore = openStore(bookAccess(bookPath))) {
      assertEquals(
          List.of(
              declaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z")),
              declaredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  dev.erst.fingrind.core.AccountType.REVENUE,
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          listAccounts(restoredStore));
    }
    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement store passphrase", "rotated-store-key".toCharArray());
        SqlitePostingFactStore replacementKeyStore =
            new SqlitePostingFactStore(
                bookPath, replacementPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> replacementKeyStore.listAccounts(firstAccountPage()));
      assertProtectedBookVerificationFailure(exception);
    }
  }

  @Test
  void rekeyBook_preservesOpenDatabaseWhenNativeRekeyFailsBeforeClose() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      setStoreDatabase(
          postingFactStore,
          new SqliteNativeDatabase(
              requireStoreDatabase(postingFactStore).handle(),
              SqliteNativeApiTestSupport.withRekey(
                  SqliteNativeBootstrap.api(),
                  constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class))));
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "rekey native failure", "rotated-store-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.rekeyBook(replacementPassphrase, REKEYED_AT));
        assertTrue(NullTestSupport.messageOf(exception).contains("Failed to rekey SQLite book."));
        assertNotNull(storeDatabase(postingFactStore));
      }
    }
  }
}
