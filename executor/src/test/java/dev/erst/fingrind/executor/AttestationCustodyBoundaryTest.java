package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the executor-owned file-custody adapters without exposing private key material. */
class AttestationCustodyBoundaryTest {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");

  @TempDir Path temporaryDirectory;

  @Test
  void createsGenesisAndScopesExistingCredentialUseToOneExecutorAction() throws IOException {
    AttestationCredentialSource source = createCredentialSource("founder");
    AttestationEvidence genesis =
        AttestationGenesisFactory.create(
            ExecutorAccountingTestSupport.bookIdentity(),
            RECORDED_AT,
            List.of(
                new AttestationFounderInput(
                    PRINCIPAL_ID, source.encryptedKeyFilePath(), source.passphraseFilePath())));

    assertEquals(0, AttestationVerifier.verifyBook(List.of(genesis)).headOrder().intValueExact());
    try (AttestationSigningSession session =
        AttestationSigningSessionFactory.open(List.of(source))) {
      assertNotNull(session);
    }
    assertEquals(
        "authorized",
        AttestationMutationAuthorization.withAuthorizer(List.of(source), ignored -> "authorized"));
  }

  @Test
  void classifiesInvalidOrEmptyCredentialSelectionsWithoutCreatingAReplacementKey()
      throws IOException {
    Path passphrasePath = temporaryDirectory.resolve("missing.passphrase");
    Path invalidKeyPath = temporaryDirectory.resolve("invalid.fgatk");
    Files.writeString(passphrasePath, "test attestation passphrase\n");
    Files.writeString(invalidKeyPath, "not an encrypted attestation key");
    AttestationCredentialSource invalidSource =
        new AttestationCredentialSource(PRINCIPAL_ID, invalidKeyPath, passphrasePath);

    IllegalArgumentException emptySigningSessionSelection =
        assertThrows(
            IllegalArgumentException.class, () -> AttestationSigningSessionFactory.open(List.of()));
    assertEquals(
        "Protected-book mutation requires one through five attestation credentials.",
        emptySigningSessionSelection.getMessage());
    AttestationCredentialException invalidSigningCredential =
        assertThrows(
            AttestationCredentialException.class,
            () -> AttestationSigningSessionFactory.open(List.of(invalidSource)));
    assertEquals(invalidSource.encryptedKeyFilePath(), invalidSigningCredential.credentialPath());
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationMutationAuthorization.withAuthorizer(List.of(), ignored -> "unreachable"));
    assertThrows(
        AttestationCredentialException.class,
        () ->
            AttestationMutationAuthorization.withAuthorizer(
                List.of(invalidSource), ignored -> "unreachable"));
    assertThrows(
        AttestationCredentialException.class,
        () ->
            AttestationGenesisFactory.create(
                ExecutorAccountingTestSupport.bookIdentity(),
                RECORDED_AT,
                List.of(
                    new AttestationFounderInput(
                        PRINCIPAL_ID,
                        invalidSource.encryptedKeyFilePath(),
                        invalidSource.passphraseFilePath()))));
  }

  @Test
  void preservesActionFailuresInsteadOfMisclassifyingThemAsCredentialFailures() throws IOException {
    AttestationCredentialSource source = createCredentialSource("founder");

    IllegalArgumentException actionFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AttestationMutationAuthorization.withAuthorizer(
                    List.of(source),
                    ignored -> {
                      throw new IllegalArgumentException("The posting request is invalid.");
                    }));

    assertEquals("The posting request is invalid.", actionFailure.getMessage());
  }

  @Test
  void preservesAllPublishedPassphraseSourceAlternativesAtTheMaintenanceBoundary() {
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    Path keyPath = temporaryDirectory.resolve("book.key");
    for (BookAccess.PassphraseSource source :
        List.of(
            new BookAccess.PassphraseSource.KeyFile(keyPath),
            BookAccess.PassphraseSource.StandardInput.INSTANCE,
            BookAccess.PassphraseSource.InteractivePrompt.INSTANCE)) {
      ProtectedBookPassphraseSource local = ProtectedBookPassphraseSource.fromPublished(source);
      assertPassphraseSourceRoundTrip(bookPath, source, local);
    }
  }

  private static void assertPassphraseSourceRoundTrip(
      Path bookPath, BookAccess.PassphraseSource source, ProtectedBookPassphraseSource local) {
    assertEquals(source, local.toPublished());
    assertInstanceOf(BookAccess.class, new ProtectedBookAccess(bookPath, local).toPublished());
  }

  private AttestationCredentialSource createCredentialSource(String name) throws IOException {
    Path keyPath = temporaryDirectory.resolve(name + ".fgatk");
    Path passphrasePath = temporaryDirectory.resolve(name + ".passphrase");
    char[] passphrase = "test attestation passphrase".toCharArray();
    try {
      AttestationKeyFiles.create(keyPath, passphrase);
      Files.writeString(passphrasePath, "test attestation passphrase\n");
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    return new AttestationCredentialSource(PRINCIPAL_ID, keyPath, passphrasePath);
  }
}
