package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookAccess;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqliteBookAccessSelectionTest extends SqlitePostingFactStoreTestSupport {

  @Test
  void constructor_rejectsNonKeyFileAccessSelection() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "SQLite same-package file-backed stores require a --book-key-file access selection, not --book-passphrase-stdin.",
        exception.getMessage());
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

    assertTrue(exception.getMessage().contains("must contain a UTF-8 passphrase"));
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
  void takeBookPassphrase_rejectsSecondConsumption() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("passphrase-consumption.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test passphrase consumption", TEST_BOOK_KEY.toCharArray()))) {
      try (SqliteBookPassphrase ignored =
          SqliteStoreTestAccess.takePendingPassphrase(postingFactStore)) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteStoreTestAccess.takePendingPassphrase(postingFactStore));

        assertEquals("SQLite book passphrase is no longer available.", exception.getMessage());
      }
    }
  }

  @Test
  void openBook_remembersConsumedPassphraseFailureAsTerminalState() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("consumed-passphrase.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test consumed passphrase", TEST_BOOK_KEY.toCharArray()))) {
      try (SqliteBookPassphrase ignored =
          SqliteStoreTestAccess.takePendingPassphrase(postingFactStore)) {
        IllegalStateException firstFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
        IllegalStateException secondFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

        assertEquals("SQLite book passphrase is no longer available.", firstFailure.getMessage());
        assertSame(firstFailure, secondFailure);
      }
    }
  }
}
