package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.canonicalPublicationPath;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.canonicalTemporaryDirectory;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.directoryEntryCount;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.privateOwnerOnlyChildDirectory;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.privateOwnerOnlyDirectory;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exercises retained-stage, no-clobber key-file publication. */
class AttestationKeyFilePublicationTest extends AttestationKeyFileTestFixture {

  @Test
  void keyFilePublicationRetainsTheExactPrivateStageAlongsideTheFinalKey() throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "keys");
    Path keyPath = keyDirectory.resolve("signing.pk8");
    byte[] encryptedPrivateKey = new byte[] {1, 2, 3};

    ArtifactPublicationResult publication =
        AttestationKeyFilePublication.writeNewKeyFile(keyPath, encryptedPrivateKey);

    assertEquals(canonicalPublicationPath(keyPath), publication.publishedArtifactPath());
    assertArrayEquals(encryptedPrivateKey, Files.readAllBytes(publication.publishedArtifactPath()));
    assertArrayEquals(
        encryptedPrivateKey, Files.readAllBytes(publication.retention().retainedStagePath()));
    assertTrue(Files.isRegularFile(publication.retention().retainedStagePath()));
  }

  @Test
  void occupiedFinalKeyRetainsTheFreshStageAndNeverChangesTheExistingFile() throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "occupied-keys");
    Path keyPath = keyDirectory.resolve("signing.pk8");
    byte[] existing = new byte[] {9, 9};
    Files.write(keyPath, existing);

    AttestationKeyFileDestinationOccupiedException failure =
        assertThrows(
            AttestationKeyFileDestinationOccupiedException.class,
            () -> AttestationKeyFilePublication.writeNewKeyFile(keyPath, new byte[] {1, 2, 3}));

    assertEquals(canonicalPublicationPath(keyPath), failure.keyFilePath());
    assertArrayEquals(existing, Files.readAllBytes(keyPath));
    assertTrue(Files.isRegularFile(failure.retainedStage().retainedStagePath()));
    assertArrayEquals(
        new byte[] {1, 2, 3}, Files.readAllBytes(failure.retainedStage().retainedStagePath()));
  }

  @Test
  void keyFilePublicationRejectsDirectOutputParentAliasesBeforeStaging() throws Exception {
    Path physicalOutputDirectory =
        privateOwnerOnlyDirectory(temporaryDirectory, "physical-key-output");
    Path directParentAlias =
        canonicalTemporaryDirectory(temporaryDirectory).resolve("direct-key-output-alias");
    try {
      Files.createSymbolicLink(directParentAlias, physicalOutputDirectory);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }
    Path keyPath = directParentAlias.resolve("signing.pk8");

    PrivateOutputDirectory.Violation failure =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () -> AttestationKeyFilePublication.writeNewKeyFile(keyPath, new byte[] {1, 2, 3}));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, failure.kind());
    assertFalse(Files.exists(physicalOutputDirectory.resolve("signing.pk8")));
    assertEquals(0L, directoryEntryCount(physicalOutputDirectory));
  }

  @Test
  void keyFilePublicationRejectsIntermediateOutputParentAliasesBeforeStaging() throws Exception {
    Path safeRoot = privateOwnerOnlyDirectory(temporaryDirectory, "safe-key-root");
    Path safeOutputDirectory = privateOwnerOnlyChildDirectory(safeRoot, "keys");
    Path intermediateAlias =
        canonicalTemporaryDirectory(temporaryDirectory).resolve("key-root-alias");
    try {
      Files.createSymbolicLink(intermediateAlias, safeRoot);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException exception) {
      assumeTrue(false, "The filesystem does not permit symbolic-link test fixtures.");
      return;
    }
    Path requestedKeyPath = intermediateAlias.resolve("keys/signing.pk8");

    PrivateOutputDirectory.Violation failure =
        assertThrows(
            PrivateOutputDirectory.Violation.class,
            () ->
                AttestationKeyFilePublication.writeNewKeyFile(
                    requestedKeyPath, new byte[] {1, 2, 3}));

    assertEquals(PrivateOutputDirectory.Violation.Kind.PATH_COLLISION, failure.kind());
    assertEquals(0L, directoryEntryCount(safeOutputDirectory));
  }

  @Test
  void keyFilePublicationRejectsGroupWritableOutputParentBeforeStaging() throws Exception {
    assumeTrue(
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
    Path unsafeOutputDirectory =
        Files.createDirectories(
            canonicalTemporaryDirectory(temporaryDirectory).resolve("unsafe-key-output"));
    Files.setPosixFilePermissions(
        unsafeOutputDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE));

    assertThrows(
        PrivateOutputDirectory.Violation.class,
        () ->
            AttestationKeyFilePublication.writeNewKeyFile(
                unsafeOutputDirectory.resolve("signing.pk8"), new byte[] {1, 2, 3}));
  }

  @Test
  void keyFilePublicationRejectsTheFilesystemRootAsAKeyPath() {
    Path root = temporaryDirectory.toAbsolutePath().getRoot();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> AttestationKeyFilePublication.writeNewKeyFile(root, new byte[] {1, 2, 3}));

    assertEquals("Attestation key file must have a parent directory.", exception.getMessage());
  }

  @Test
  void linkFailureRetainsTheStageAndReportsTheExactIndeterminateFinalCandidate() throws Exception {
    Path stage = Files.createFile(temporaryDirectory.resolve("retained-stage.tmp"));
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(stage);
    Path absentParent = temporaryDirectory.resolve("absent");
    Path finalPath = absentParent.resolve("signing.pk8");

    ArtifactPublicationOutcomeUncertainException exception =
        assertThrows(
            ArtifactPublicationOutcomeUncertainException.class,
            () ->
                AttestationKeyFilePublisher.linkFinalPath(
                    new AttestationKeyFileDestination(absentParent, finalPath), retention));

    assertEquals(finalPath.toAbsolutePath().normalize(), exception.candidateArtifactPath());
    assertEquals(retention, exception.retainedStage());
  }

  @Test
  void directoryForceFailureRetainsTheCompletePublishedKeyFact() throws Exception {
    Path stage = Files.createFile(temporaryDirectory.resolve("retained-stage.tmp"));
    Path finalPath = temporaryDirectory.resolve("signing.pk8");
    Files.createLink(finalPath, stage);
    Path absentParent = temporaryDirectory.resolve("absent");
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(stage);

    AttestationKeyFilePublicationDurabilityException exception =
        assertThrows(
            AttestationKeyFilePublicationDurabilityException.class,
            () ->
                AttestationKeyFilePublisher.forceFinalLink(
                    new AttestationKeyFileDestination(absentParent, finalPath), retention));

    assertEquals(
        finalPath.toAbsolutePath().normalize(), exception.publication().publishedArtifactPath());
    assertEquals(retention, exception.publication().retention());
    assertTrue(exception.getCause() instanceof IOException);
  }

  @Test
  void credentialCreationExposesTheRetainedPublicationStage() throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "credential-keys");
    AttestationKeyFileCreation creation =
        AttestationFilePkcs8Custodian.createCredential(
            keyDirectory.resolve("signing.pk8"), "correct horse battery staple".toCharArray());

    assertEquals(creation.publication().retention(), creation.retainedStage());
  }
}
