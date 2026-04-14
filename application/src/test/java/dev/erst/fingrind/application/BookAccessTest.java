package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests for the protected-book access tuple. */
class BookAccessTest {
  @Test
  void constructor_retainsBookAndKeyPaths() {
    Path bookFilePath = Path.of("books", "acme.sqlite");
    Path bookKeyFilePath = Path.of("keys", "acme.book-key");

    BookAccess access =
        new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));

    assertEquals(bookFilePath, access.bookFilePath());
    assertEquals(
        new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath), access.passphraseSource());
  }

  @Test
  void passphraseSources_publishCanonicalOptionNames() {
    assertEquals(
        "--book-key-file",
        new BookAccess.PassphraseSource.KeyFile(Path.of("keys", "acme.book-key")).optionName());
    assertEquals(
        "--book-passphrase-stdin", BookAccess.PassphraseSource.StandardInput.INSTANCE.optionName());
    assertEquals(
        "--book-passphrase-prompt",
        BookAccess.PassphraseSource.InteractivePrompt.INSTANCE.optionName());
  }
}
