package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Tests for the protected-book access tuple. */
class BookAccessTest {
  @Test
  void constructor_retainsBookAndKeyPaths() {
    Path bookFilePath = Path.of("books", "acme.sqlite");
    Path bookKeyFilePath = Path.of("keys", "acme.book-key");

    BookAccess access =
        new BookAccess(
            bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath), List.of());

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

  @Test
  void attestationCredentialSources_requireOneToFiveDistinctPrincipalsAndKeyFiles() {
    AttestationCredentialSource first = credential("first", "first-key");
    AttestationCredentialSource second = credential("second", "second-key");
    BookAccess access =
        new BookAccess(
            Path.of("books", "acme.sqlite"),
            BookAccess.PassphraseSource.StandardInput.INSTANCE,
            List.of(first, second));

    assertIterableEquals(List.of(first, second), access.requireAttestationCredentialSources());
    assertThrows(
        IllegalStateException.class,
        () ->
            new BookAccess(
                    Path.of("books", "acme.sqlite"),
                    BookAccess.PassphraseSource.StandardInput.INSTANCE,
                    List.of())
                .requireAttestationCredentialSources());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                List.of(first, credential("first", "third-key"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                List.of(first, credential("third", "first-key"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                List.of(
                    credential("one", "one-key"),
                    credential("two", "two-key"),
                    credential("three", "three-key"),
                    credential("four", "four-key"),
                    credential("five", "five-key"),
                    credential("six", "six-key"))));
  }

  private static AttestationCredentialSource credential(String principal, String keyName) {
    return new AttestationCredentialSource(
        UUID.nameUUIDFromBytes(principal.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        Path.of("keys", keyName + ".fgatk"),
        Path.of("keys", keyName + ".passphrase"));
  }
}
