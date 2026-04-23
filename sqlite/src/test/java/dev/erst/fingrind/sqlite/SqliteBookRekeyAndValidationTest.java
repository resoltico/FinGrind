package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookRekeyAndValidationTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void rekeyBook_contractLevelResolverUsesNewSecretIntentAndSurfacesRejections() throws Exception {
    Path acceptedBookPath = tempDirectory.resolve("rekey-contract-level.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(acceptedBookPath))) {
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
              });

      assertEquals(
          new RekeyBookResult.Rekeyed(acceptedBookPath.toAbsolutePath().normalize()),
          acceptedDecision.requireAccepted());
    }

    try (SqlitePostingFactStore rejectedStore =
        new SqlitePostingFactStore(
            bookAccess(tempDirectory.resolve("rekey-contract-rejected.sqlite")))) {
      initializeBookWithDefaultAccounts(rejectedStore);

      ContractDecision<RekeyBookResult> rejectedDecision =
          rejectedStore.rekeyBook(
              BookAccess.PassphraseSource.StandardInput.INSTANCE,
              (resolvedBookPath, passphraseSource, intent) ->
                  ContractDecision.rejected(
                      ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
                          "Rejected replacement secret", null, null)));

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

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement store passphrase", "rotated-store-key".toCharArray())) {
        assertEquals(
            new dev.erst.fingrind.contract.RekeyBookResult.Rekeyed(
                bookPath.toAbsolutePath().normalize()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
    }

    try (SqlitePostingFactStore oldKeyStore = new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> oldKeyStore.listAccounts(firstAccountPage()));
      assertInvalidPlaintextBookFailure(exception);
    }

    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement store passphrase", "rotated-store-key".toCharArray());
        SqlitePostingFactStore rotatedStore =
            new SqlitePostingFactStore(
                bookPath, replacementPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
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
          listAccounts(rotatedStore));
      assertEquals("delete", queryText(storeDatabase(rotatedStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(rotatedStore), "pragma synchronous"));
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
            new dev.erst.fingrind.contract.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
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
            new dev.erst.fingrind.contract.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
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
                initializedBookPath, bookPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
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
  void transactionValidationBook_wrapsNativeFailuresForStateAndAccountLookups() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-stale.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(bookPath), postingFactStore.postingReader());

      IllegalStateException initializedFailure =
          assertThrows(IllegalStateException.class, validationBook::isInitialized);
      assertTrue(initializedFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException accountFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findAccount(new AccountCode("1000")));
      assertTrue(accountFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException postingFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findExistingPosting(new IdempotencyKey("idem-1")));
      assertTrue(postingFailure.getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  void transactionValidationBook_findsDeclaredAccountsThroughBatchLookup() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-success.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              storeDatabase(postingFactStore), postingFactStore.postingReader());

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
            bookAccess(bookPath), SqliteStoreAccessMode.READ_WRITE_CREATE, sqliteApi::get)) {
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
                () -> postingFactStore.rekeyBook(replacementPassphrase));

        assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
        assertNull(storeDatabase(postingFactStore));
      }
    }
  }

  @Test
  void rekeyBook_preservesOpenDatabaseWhenNativeRekeyFailsBeforeClose() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
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
                () -> postingFactStore.rekeyBook(replacementPassphrase));

        assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
        assertNotNull(storeDatabase(postingFactStore));
      }
    }
  }
}
