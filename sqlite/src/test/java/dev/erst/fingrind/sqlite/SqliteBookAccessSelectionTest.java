package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookAccessSelectionTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void requireKeyFile_rejectsNonKeyFilePassphraseSource() {
    var decision =
        SqliteBookAccessRules.requireKeyFile(BookAccess.PassphraseSource.StandardInput.INSTANCE);
    switch (decision) {
      case dev.erst.fingrind.contract.runtime.ContractDecision.Accepted<Path>(Path ignored) ->
          throw new AssertionError("Expected stdin selection to be rejected.");
      case dev.erst.fingrind.contract.runtime.ContractDecision.Rejected<Path>(var failure) -> {
        assertEquals(
            ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(), failure.code());
        assertEquals(
            "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-stdin.",
            failure.message());
      }
    }
  }

  @Test
  void requireKeyFile_acceptsKeyFilesAndRejectsInteractivePrompt() {
    Path keyFilePath = tempDirectory.resolve("interactive.key");
    var accepted =
        SqliteBookAccessRules.requireKeyFile(new BookAccess.PassphraseSource.KeyFile(keyFilePath));
    switch (accepted) {
      case dev.erst.fingrind.contract.runtime.ContractDecision.Accepted<Path>(Path path) ->
          assertEquals(keyFilePath, path);
      case dev.erst.fingrind.contract.runtime.ContractDecision.Rejected<Path>(var failure) ->
          throw new AssertionError("Expected key-file selection to be accepted: " + failure.code());
    }

    var rejected =
        SqliteBookAccessRules.requireKeyFile(
            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE);
    switch (rejected) {
      case dev.erst.fingrind.contract.runtime.ContractDecision.Accepted<Path>(Path ignored) ->
          throw new AssertionError("Expected interactive prompt selection to be rejected.");
      case dev.erst.fingrind.contract.runtime.ContractDecision.Rejected<Path>(var failure) -> {
        assertEquals(
            ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.code(), failure.code());
        assertEquals(
            "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-prompt.",
            failure.message());
      }
    }
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
                openStore(
                    new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath))));
    assertTrue(NullTestSupport.messageOf(exception).contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void loadPassphrase_loadsKeyFileBackedAccessSelection() throws Exception {
    Path keyFile = tempDirectory.resolve("book-passphrase.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);
    try (SqliteBookPassphrase passphrase =
        loadPassphrase(
            new BookAccess(
                tempDirectory.resolve("book-passphrase.sqlite"),
                new BookAccess.PassphraseSource.KeyFile(keyFile)))) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void sessionReopensCleanlyAfterDatabaseStateReset() throws Exception {
    Path bookPath = tempDirectory.resolve("session-reopen.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
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
