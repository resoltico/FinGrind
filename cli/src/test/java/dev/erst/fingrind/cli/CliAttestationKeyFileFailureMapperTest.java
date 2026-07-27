package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.attestation.AttestationKeyFileDestinationOccupiedException;
import dev.erst.fingrind.core.attestation.AttestationKeyFilePublicationDurabilityException;
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
  void creationFailure_mapsNoClobberAndPublicationFactsWithoutLosingTheirPaths() {
    Path keyFile = temporaryDirectory.resolve("operator.fgatk");
    Path residualStage = temporaryDirectory.resolve(".operator.fgatk-stage");
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(residualStage);
    ArtifactPublicationResult publication =
        ArtifactPublicationResult.restoreCapturedCanonicalPaths(keyFile, retention);

    ContractFailure occupied =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new AttestationKeyFileDestinationOccupiedException(
                keyFile, retention, new FileAlreadyExistsException(keyFile.toString())),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(occupied, ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, keyFile);

    ContractFailure durability =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new AttestationKeyFilePublicationDurabilityException(
                publication, new IOException("directory force failed")),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(
        durability, ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN, keyFile);
    assertEquals(
        publication,
        assertInstanceOf(
                ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class,
                durability.details())
            .publication());

    ContractFailure retainedStage =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new ArtifactPublicationRetainedStageException(
                retention, new IOException("stage creation failed")),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(retainedStage, ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, keyFile);
    assertEquals(retention, retainedStage.retainedStage());

    ContractFailure outcome =
        CliAttestationKeyFileFailureMapper.creationFailure(
            new ArtifactPublicationOutcomeUncertainException(
                keyFile, retention, new IOException("link outcome unknown")),
            keyFile,
            ProtocolOptions.Attestation.NEW_KEY_FILE);
    assertFailure(
        outcome, ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN, keyFile);
    assertEquals(
        retention,
        assertInstanceOf(
                ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class, outcome.details())
            .retainedStage());
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
