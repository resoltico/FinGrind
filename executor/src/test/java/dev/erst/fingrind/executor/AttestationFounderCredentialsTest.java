package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies founder credential opening exposes transaction recovery without a staging-path leak. */
class AttestationFounderCredentialsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void translatesAnIncompletePublicationToItsCandidateAndTransactionResult() {
    Path keyPath = temporaryDirectory.resolve("founder.fgatk");
    PublicationTransactionExecutionException publicationFailure =
        new PublicationTransactionExecutionException(
            PublicationTransactionTestFixtures.incompleteResult(), new IOException("injected"));

    AttestationFounderKeyPublicationTransactionException observed =
        assertThrows(
            AttestationFounderKeyPublicationTransactionException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(keyPath),
                    ignored -> {
                      throw publicationFailure;
                    }));

    assertEquals(keyPath.toAbsolutePath().normalize(), observed.candidateArtifactPath());
    assertEquals(publicationFailure.result(), observed.transactionResult());
    assertSame(publicationFailure, observed.getCause());
  }

  @Test
  void translatesAnOrdinaryCustodyFailureToTheCredentialBoundary() {
    Path keyPath = temporaryDirectory.resolve("founder.fgatk");

    AttestationCredentialException observed =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationFounderCredentials.openOrCreate(
                    founderInput(keyPath),
                    ignored -> {
                      throw new IOException("passphrase file cannot be read");
                    }));

    assertEquals(keyPath.toAbsolutePath().normalize(), observed.credentialPath());
  }

  @Test
  void translatesMissingAndUnreadableFounderInputsAtTheCredentialBoundary() throws IOException {
    Path missingKeyPath = temporaryDirectory.resolve("missing-founder.fgatk");

    AttestationCredentialException missingCredential =
        assertThrows(
            AttestationCredentialException.class,
            () -> AttestationFounderCredentials.openExisting(founderInput(missingKeyPath)));
    assertEquals(missingKeyPath.toAbsolutePath().normalize(), missingCredential.credentialPath());
    assertInstanceOf(IOException.class, missingCredential.getCause());

    Path readablePassphrase = temporaryDirectory.resolve("founder.passphrase");
    java.nio.file.Files.writeString(readablePassphrase, "test attestation passphrase\n");
    AttestationFounderCredentials.validateForOpening(
        founderInput(missingKeyPath, readablePassphrase));

    AttestationCredentialException unreadablePassphrase =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationFounderCredentials.validateForOpening(
                    founderInput(
                        missingKeyPath, temporaryDirectory.resolve("missing-founder.passphrase"))));
    assertEquals(
        missingKeyPath.toAbsolutePath().normalize(), unreadablePassphrase.credentialPath());
    assertInstanceOf(IOException.class, unreadablePassphrase.getCause());
  }

  @Test
  void rejectsSuccessfulTransactionsAndRestoresTheCandidateAfterSerialization() throws Exception {
    Path keyPath = temporaryDirectory.resolve("nested").resolve("..").resolve("founder.fgatk");

    IllegalArgumentException successfulResult =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AttestationFounderKeyPublicationTransactionException(
                    keyPath,
                    PublicationTransactionTestFixtures.completedResult(),
                    new IOException("publication unexpectedly completed")));
    assertEquals(
        "A founder-key publication failure cannot carry a successful transaction result.",
        successfulResult.getMessage());

    PublicationTransactionResult incomplete = PublicationTransactionTestFixtures.incompleteResult();
    AttestationFounderKeyPublicationTransactionException restored =
        roundTrip(
            new AttestationFounderKeyPublicationTransactionException(
                keyPath, incomplete, new IOException("publication incomplete")));

    assertEquals(keyPath.toAbsolutePath().normalize(), restored.candidateArtifactPath());
    assertEquals(incomplete, restored.transactionResult());
    assertInstanceOf(IOException.class, restored.getCause());
  }

  private static AttestationFounderInput founderInput(Path keyPath) {
    return founderInput(keyPath, keyPath.resolveSibling("founder.passphrase"));
  }

  private static AttestationFounderInput founderInput(Path keyPath, Path passphrasePath) {
    return new AttestationFounderInput(
        AttestationCustodian.FILE_PKCS8,
        UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
        keyPath,
        passphrasePath);
  }

  private static AttestationFounderKeyPublicationTransactionException roundTrip(
      AttestationFounderKeyPublicationTransactionException exception)
      throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(exception);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return AttestationFounderKeyPublicationTransactionException.class.cast(input.readObject());
    }
  }
}
