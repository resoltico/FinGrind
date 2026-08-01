package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies attestation command option and credential-selection admission rules. */
class CliAttestationArgumentSelectionTest extends CliAttestationTransportFixtures {
  @Test
  void parsersRequireOneReceiptFileAndRejectDuplicateOrUnsupportedAttestationOptions() {
    assertInstanceOf(
        VerifyBookAttestation.class,
        CliAttestationArguments.parseVerifyBookCommand(
            List.of(
                "verify-book",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--require-clean-attestation",
                "--output",
                "text")));
    assertInstanceOf(
        AttestationReview.class,
        CliAttestationArguments.parseAttestationReviewCommand(
            List.of(
                "attestation-review",
                "--book-file",
                "book.sqlite",
                "--book-key-file",
                "book.key",
                "--output",
                "json")));

    CliArgumentsException missingReceipt =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseExportReceiptCommand(
                    List.of(
                        "export-attestation-receipt",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key")));
    assertEquals("--receipt-file", missingReceipt.argument());

    CliArgumentsException duplicateReceipt =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseVerifyReceiptCommand(
                    List.of(
                        "verify-receipt",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key",
                        "--receipt-file",
                        "one.fgr",
                        "--receipt-file",
                        "two.fgr")));
    assertEquals("--receipt-file", duplicateReceipt.argument());

    CliArgumentsException unsupported =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliAttestationArguments.parseVerifyBookCommand(
                    List.of(
                        "verify-book",
                        "--book-file",
                        "book.sqlite",
                        "--book-key-file",
                        "book.key",
                        "--receipt-file",
                        "unexpected.fgr")));
    assertEquals("--receipt-file", unsupported.argument());
  }

  @Test
  void credentialSelections_areOptionalOnlyWhenEntirelyAbsentAndMustOtherwiseBeAligned() {
    CliAttestationCredentialArguments credentials = new CliAttestationCredentialArguments();
    assertFalse(credentials.apply("--not-an-attestation-option", List.<String>of().listIterator()));
    assertEquals(List.of(), credentials.resolveOptional());

    applyCredential(credentials, "--attestation-custodian", "file-pkcs8");
    applyCredential(credentials, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(credentials, "--attestation-key-file", "keys/principal.fgatk");
    applyCredential(credentials, "--attestation-passphrase-file", "keys/principal.passphrase");
    UUID secondPrincipal = UUID.fromString("4b4a38fa-cf41-4d53-afc1-8d4e6cdf438c");
    applyCredential(credentials, "--attestation-principal-id", secondPrincipal.toString());
    applyCredential(credentials, "--attestation-key-file", "keys/second-principal.fgatk");
    applyCredential(
        credentials, "--attestation-passphrase-file", "keys/second-principal.passphrase");
    assertEquals(
        List.of(BOOK_ID, secondPrincipal),
        credentials.resolveOptional().stream().map(source -> source.principalId()).toList());
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        credentials.resolveOptional().getFirst().custodian());

    CliAttestationCredentialArguments incomplete = new CliAttestationCredentialArguments();
    applyCredential(incomplete, "--attestation-principal-id", BOOK_ID.toString());
    assertThrows(IllegalArgumentException.class, incomplete::resolveOptional);

    CliAttestationCredentialArguments noPrincipal = new CliAttestationCredentialArguments();
    applyCredential(noPrincipal, "--attestation-key-file", "keys/principal.fgatk");
    assertThrows(IllegalArgumentException.class, noPrincipal::resolveOptional);

    CliAttestationCredentialArguments passphraseWithoutPrincipal =
        new CliAttestationCredentialArguments();
    applyCredential(
        passphraseWithoutPrincipal, "--attestation-passphrase-file", "keys/principal.passphrase");
    assertThrows(IllegalArgumentException.class, passphraseWithoutPrincipal::resolveOptional);

    CliAttestationCredentialArguments keyWithoutPassphrase =
        new CliAttestationCredentialArguments();
    applyCredential(keyWithoutPassphrase, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(keyWithoutPassphrase, "--attestation-key-file", "keys/principal.fgatk");
    assertThrows(IllegalArgumentException.class, keyWithoutPassphrase::resolveOptional);

    CliAttestationCredentialArguments missingCustodian = new CliAttestationCredentialArguments();
    applyCredential(missingCustodian, "--attestation-principal-id", BOOK_ID.toString());
    applyCredential(missingCustodian, "--attestation-key-file", "keys/principal.fgatk");
    applyCredential(missingCustodian, "--attestation-passphrase-file", "keys/principal.passphrase");
    CliArgumentsException missingCustodianException =
        assertThrows(CliArgumentsException.class, missingCustodian::resolveOptional);
    assertEquals("--attestation-custodian", missingCustodianException.argument());

    CliAttestationCredentialArguments custodianOnly = new CliAttestationCredentialArguments();
    applyCredential(custodianOnly, "--attestation-custodian", "file-pkcs8");
    assertThrows(IllegalArgumentException.class, custodianOnly::resolveOptional);

    CliAttestationCredentialArguments duplicateCustodian = new CliAttestationCredentialArguments();
    applyCredential(duplicateCustodian, "--attestation-custodian", "file-pkcs8");
    assertThrows(
        CliArgumentsException.class,
        () -> applyCredential(duplicateCustodian, "--attestation-custodian", "file-pkcs8"));

    CliAttestationCredentialArguments sixCredentials = credentials(6);
    assertEquals(6, sixCredentials.resolveOptional().size());

    CliAttestationCredentialArguments tooMany = credentials(65);
    assertThrows(IllegalArgumentException.class, tooMany::resolveOptional);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CliAttestationCredentialArguments.requirePresent(
                new BookAccess(
                    Path.of("book.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(Path.of("book.key")),
                    List.of())));
  }

  private static void applyCredential(
      CliAttestationCredentialArguments credentials, String option, String value) {
    ListIterator<String> iterator = List.of(value).listIterator();
    assertTrue(credentials.apply(option, iterator));
  }

  private static CliAttestationCredentialArguments credentials(int count) {
    CliAttestationCredentialArguments credentials = new CliAttestationCredentialArguments();
    applyCredential(credentials, "--attestation-custodian", "file-pkcs8");
    for (int index = 0; index < count; index++) {
      applyCredential(
          credentials,
          "--attestation-principal-id",
          "00000000-0000-4000-8000-%012d".formatted(index));
      applyCredential(
          credentials, "--attestation-key-file", "keys/principal-%d.fgatk".formatted(index));
      applyCredential(
          credentials,
          "--attestation-passphrase-file",
          "keys/principal-%d.passphrase".formatted(index));
    }
    return credentials;
  }
}
