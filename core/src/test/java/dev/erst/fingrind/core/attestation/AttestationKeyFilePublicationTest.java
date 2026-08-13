package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.canonicalPublicationPath;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.canonicalTemporaryDirectory;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.privateOwnerOnlyChildDirectory;
import static dev.erst.fingrind.core.attestation.AttestationKeyFileTestSupport.privateOwnerOnlyDirectory;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exercises journal-owned, no-clobber key-file publication. */
class AttestationKeyFilePublicationTest extends AttestationKeyFileTestFixture {

  @Test
  void keyFilePublicationReportsOnlyCompleteTransactionEvidenceAlongsideTheFinalKey()
      throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "keys");
    Path keyPath = keyDirectory.resolve("signing.pk8");
    byte[] encryptedPrivateKey = new byte[] {1, 2, 3};

    PublicationTransactionArtifact publication =
        AttestationKeyFilePublication.writeNewKeyFile(keyPath, encryptedPrivateKey);

    assertEquals(canonicalPublicationPath(keyPath), publication.publishedArtifactPath());
    assertArrayEquals(encryptedPrivateKey, Files.readAllBytes(publication.publishedArtifactPath()));
    assertTrue(publication.transactionResult().successful());
  }

  @Test
  void occupiedFinalKeyLeavesTheExistingFileUntouchedAfterTheJournalAbortsItsStage()
      throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "occupied-keys");
    Path keyPath = keyDirectory.resolve("signing.pk8");
    byte[] existing = new byte[] {9, 9};
    Files.write(keyPath, existing);

    FileAlreadyExistsException failure =
        assertThrows(
            FileAlreadyExistsException.class,
            () -> AttestationKeyFilePublication.writeNewKeyFile(keyPath, new byte[] {1, 2, 3}));

    assertArrayEquals(existing, Files.readAllBytes(keyPath));
    assertEquals(canonicalPublicationPath(keyPath).toString(), failure.getFile());
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
    assertFalse(Files.exists(physicalOutputDirectory.resolve("signing.pk8")));
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
    assertFalse(Files.exists(safeOutputDirectory.resolve("signing.pk8")));
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
  void credentialCreationExposesTheCompletedPublicationTransaction() throws Exception {
    Path keyDirectory = privateOwnerOnlyDirectory(temporaryDirectory, "credential-keys");
    AttestationKeyFileCreation creation =
        AttestationFilePkcs8Custodian.createCredential(
            keyDirectory.resolve("signing.pk8"), "correct horse battery staple".toCharArray());

    assertTrue(creation.publication().transactionResult().successful());
  }
}
