package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailureException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookAccessSelectionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void constructor_rejectsNonKeyFileAccessSelection() {
    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));
    assertEquals(
        ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(),
        exception.failure().code());
    assertEquals(
        "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-stdin.",
        exception.failure().message());
  }

  @Test
  void constructor_rejectsInvalidKeyFilePayloadBeforeStoreCreation() throws Exception {
    Path bookPath = tempDirectory.resolve("invalid-key-payload-store.sqlite");
    Path keyPath = tempDirectory.resolve("invalid-key-payload-store.key");
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
    Files.write(keyPath, new byte[] {(byte) 0xFF});
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath))));
    assertTrue(NullTestSupport.messageOf(exception).contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void passphraseFor_loadsKeyFileBackedAccessSelection() throws Exception {
    Path keyFile = tempDirectory.resolve("book-passphrase.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);
    try (SqliteBookPassphrase passphrase =
        SqlitePostingFactStore.passphraseDecisionFor(
                new BookAccess(
                    tempDirectory.resolve("book-passphrase.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(keyFile)))
            .requireAccepted()) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void sessionReopensCleanlyAfterDatabaseStateReset() throws Exception {
    Path bookPath = tempDirectory.resolve("session-reopen.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      assertTrue(postingFactStore.inspectBook().initialized());
      try (SqliteNativeDatabase firstDatabase = requireStoreDatabase(postingFactStore)) {
        clearPublishedDatabaseState(postingFactStore);
        assertNotSame(firstDatabase, postingFactStore.activeNativeDatabase());
      }
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void reusedSessionKeepsWorkingAfterStateResetForInitializationFlow() throws Exception {
    Path bookPath = tempDirectory.resolve("state-reset-initialization.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            bookPath,
            SqliteBookPassphrase.fromCharacters(
                "test session secret reuse", TEST_BOOK_KEY.toCharArray()),
            SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertTrue(postingFactStore.inspectBook().initialized());
      try (SqliteNativeDatabase firstDatabase = requireStoreDatabase(postingFactStore)) {
        assertDoesNotThrow(firstDatabase::handle);
        clearPublishedDatabaseState(postingFactStore);
      }
      assertDoesNotThrow(() -> postingFactStore.inspectBook());
    }
  }
}
