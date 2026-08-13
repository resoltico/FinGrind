package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies every deterministic attestation-key-file creation failure translation. */
class CliAttestationKeyFileFailureMapperTest {
  @TempDir Path temporaryDirectory;

  @BeforeEach
  void hardenTemporaryDirectory() {
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(temporaryDirectory);
  }

  @Test
  void creationFailure_mapsIncompletePublicationThroughItsTransactionIdentifier() {
    Path keyFile = temporaryDirectory.resolve("operator.fgatk");
    PublicationTransactionExecutionException transaction =
        new PublicationTransactionExecutionException(
            CliPublicationTransactionTestFixtures.incompleteResult(),
            new IOException("journal cleanup incomplete"));

    ContractFailure failure =
        CliAttestationKeyFileFailureMapper.creationFailure(
            transaction, keyFile, ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(failure, ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE, keyFile);
    assertEquals(
        transaction.result(),
        assertInstanceOf(
                ContractFailureDetails.PublicationTransactionIncomplete.class, failure.details())
            .transactionResult());
  }

  @Test
  void creationFailure_mapsCredentialAndLatePrivateDirectoryRefusals() throws IOException {
    Path keyFile = temporaryDirectory.resolve("operator.fgatk");
    ContractFailure ioFailure =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new IOException("passphrase file missing"),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(ioFailure, ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL, keyFile);

    ContractFailure argumentFailure =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new IllegalArgumentException("empty passphrase"),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(
        argumentFailure, ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL, keyFile);

    Assumptions.assumeTrue(
        temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
    Path looseDirectory = temporaryDirectory.resolve("late-directory-revalidation");
    Files.createDirectory(looseDirectory);
    Files.setPosixFilePermissions(
        looseDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE));
    PrivateOutputDirectory.Violation violation =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> PrivateOutputDirectory.requireExistingOwnerOnly(looseDirectory));

    ContractFailure privateDirectoryFailure =
        CliAttestationKeyFileFailureMapper.creationFailure(
            violation, keyFile, ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(
        privateDirectoryFailure,
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY,
        keyFile);
  }

  @Test
  void creationFailure_mapsASafelyAbortedNoReplaceCollisionAsTargetOccupied() {
    Path keyFile = temporaryDirectory.resolve("operator.fgatk");

    ContractFailure failure =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new FileAlreadyExistsException(keyFile.toString()),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);

    assertFailure(failure, ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, keyFile);
  }

  @Test
  void creationFailure_rethrowsUnknownRuntimeFailuresWithoutRelabelingThem() {
    IllegalStateException unknown = new IllegalStateException("unexpected key generator fault");

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                CliAttestationKeyFileFailureMapper.creationFailure(
                    unknown,
                    temporaryDirectory.resolve("operator.fgatk"),
                    ProtocolOptions.Attestation.NEW_KEY_FILE));

    assertSame(unknown, thrown);
  }

  @Test
  void creationFailure_rejectsAnImpossibleCheckedFailureFamily() {
    AssertionError thrown =
        assertThrows(
            AssertionError.class,
            () ->
                CliAttestationKeyFileFailureMapper.creationFailure(
                    new Exception("unexpected checked failure"),
                    temporaryDirectory.resolve("operator.fgatk"),
                    ProtocolOptions.Attestation.NEW_KEY_FILE));

    assertEquals(
        "Key-file creation must fail with IOException or RuntimeException.", thrown.getMessage());
  }

  private static void assertFailure(
      ContractFailure failure, ContractErrors.Descriptor descriptor, Path expectedPath) {
    assertEquals(descriptor.code(), failure.code());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        Objects.requireNonNull(failure.paths(), "failure paths").path());
    assertEquals(ProtocolOptions.Attestation.NEW_KEY_FILE, failure.argument());
  }
}
