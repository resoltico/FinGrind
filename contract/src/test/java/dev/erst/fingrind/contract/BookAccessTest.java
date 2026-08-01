package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.ArrayList;
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
  void attestationCredentialSources_preserveCandidatesForSigningAdmissionAndCapTheirCount() {
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
    assertIterableEquals(
        List.of(first, credential("first", "third-key")),
        new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                List.of(first, credential("first", "third-key")))
            .requireAttestationCredentialSources());
    assertIterableEquals(
        List.of(first, credential("third", "first-key")),
        new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                List.of(first, credential("third", "first-key")))
            .requireAttestationCredentialSources());
    List<AttestationCredentialSource> maximumCredentials =
        credentials(AttestationAuthorizationLimits.MAXIMUM_QUORUM);
    assertIterableEquals(
        maximumCredentials,
        new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                maximumCredentials)
            .requireAttestationCredentialSources());
    List<AttestationCredentialSource> tooManyCredentials = new ArrayList<>(maximumCredentials);
    tooManyCredentials.add(credential("over-limit", "over-limit-key"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookAccess(
                Path.of("books", "acme.sqlite"),
                BookAccess.PassphraseSource.StandardInput.INSTANCE,
                tooManyCredentials));
  }

  private static AttestationCredentialSource credential(String principal, String keyName) {
    return new AttestationCredentialSource(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        UUID.nameUUIDFromBytes(principal.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        Path.of("keys", keyName + ".fgatk"),
        Path.of("keys", keyName + ".passphrase"));
  }

  private static List<AttestationCredentialSource> credentials(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> credential("principal-" + index, "key-" + index))
        .toList();
  }
}
