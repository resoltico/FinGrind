package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the retained-stage no-clobber protocol for generated protected-book key files. */
class SqliteBookKeyFileGeneratorTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void generate_publicFactoryPublishesOneSecureKeyAndRetainsItsExactStage() throws Exception {
    Path keyFile = tempDirectory.resolve("public-acme.book-key");

    GeneratedBookKeyFile generatedKeyFile = SqliteBookKeyFileGenerator.generate(keyFile);

    ArtifactPublicationResult publication = generatedKeyFile.publication();
    Path retainedStage = publication.retention().retainedStagePath();
    assertEquals(keyFile.toAbsolutePath().normalize(), publication.publishedArtifactPath());
    assertTrue(Files.isRegularFile(keyFile));
    assertTrue(Files.isRegularFile(retainedStage));
    assertTrue(Files.isSameFile(keyFile, retainedStage));
    assertEquals(
        Files.readString(keyFile, StandardCharsets.UTF_8), Files.readString(retainedStage));
    assertEquals("base64url-no-padding", generatedKeyFile.encoding());
    assertEquals(256, generatedKeyFile.entropyBits());
    if (supportsPosix(keyFile)) {
      assertEquals("0600", generatedKeyFile.permissions());
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(keyFile));
      assertEquals(
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(retainedStage));
    }
    String generatedSecret = Files.readString(keyFile, StandardCharsets.UTF_8);
    assertTrue(generatedSecret.matches("[A-Za-z0-9_-]{43}"));
    try (SqliteBookPassphrase passphrase = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(
          generatedSecret.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void generate_retainsPriorOwnedAndUnownedStagesRatherThanDeletingEvidence() throws Exception {
    Path keyFile = tempDirectory.resolve("preserved-residue.book-key");
    SqliteOwnedStagedArtifact priorOwnedStage =
        SqliteOwnedStagedArtifact.create(keyFile, ".generated-key-", ".tmp");
    Files.writeString(priorOwnedStage.stagedPath(), "interrupted", StandardCharsets.UTF_8);
    Path unownedLookalike =
        keyFile.resolveSibling(keyFile.getFileName() + ".generated-key-unowned.tmp");
    Files.writeString(unownedLookalike, "unowned", StandardCharsets.UTF_8);

    GeneratedBookKeyFile generatedKeyFile = SqliteBookKeyFileGenerator.generate(keyFile);

    assertTrue(Files.isRegularFile(generatedKeyFile.publication().publishedArtifactPath()));
    assertTrue(Files.isRegularFile(priorOwnedStage.stagedPath()));
    assertEquals(
        "interrupted", Files.readString(priorOwnedStage.stagedPath(), StandardCharsets.UTF_8));
    assertTrue(Files.exists(unownedLookalike));
    priorOwnedStage.releaseRetained();
  }

  @Test
  void generateDecision_retainsTheFreshStageWhenFinalTargetBecomesOccupied() {
    Path keyFile = tempDirectory.resolve("collision.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                (finalPath, stagedPath) -> {
                  throw new FileAlreadyExistsException(finalPath.toString());
                },
                ignored -> {})
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, failure.descriptor());
    assertRetainedStage(failure, keyFile, false);
  }

  @Test
  void generateDecision_reportsAnIndeterminateFinalLinkAndRetainsBothNames() throws Exception {
    Path keyFile = tempDirectory.resolve("uncertain-link.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                (finalPath, stagedPath) -> {
                  Files.createLink(finalPath, stagedPath);
                  throw new IOException("simulated post-link uncertainty");
                },
                ignored -> {})
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN, failure.descriptor());
    assertInstanceOf(
        ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class, failure.details());
    assertRetainedStage(failure, keyFile, true);
  }

  @Test
  void generateDecision_reportsUnconfirmedDirectoryDurabilityAfterTheFinalLink() {
    Path keyFile = tempDirectory.resolve("durability.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                Files::createLink,
                ignored -> {
                  throw new IOException("simulated directory force failure");
                })
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN, failure.descriptor());
    ContractFailureDetails.ArtifactPublicationDurabilityUncertain details =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class, failure.details());
    assertEquals(
        keyFile.toAbsolutePath().normalize(), details.publication().publishedArtifactPath());
    assertRetainedStage(failure, keyFile, true);
  }

  @Test
  void generateDecision_reportsUnconfirmedDirectoryDurabilityAfterTheFinalLinkRuntimeFailure() {
    Path keyFile = tempDirectory.resolve("durability-runtime.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                Files::createLink,
                ignored -> {
                  throw new IllegalStateException("simulated directory force runtime failure");
                })
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_DURABILITY_UNCERTAIN, failure.descriptor());
    assertRetainedStage(failure, keyFile, true);
  }

  @Test
  void generateDecision_reportsAnIndeterminateRuntimeFinalLinkAndRetainsBothNames()
      throws Exception {
    Path keyFile = tempDirectory.resolve("uncertain-runtime-link.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                (finalPath, stagedPath) -> {
                  Files.createLink(finalPath, stagedPath);
                  throw new IllegalStateException("simulated post-link runtime uncertainty");
                },
                ignored -> {})
            .requireRejected();

    assertEquals(
        ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN, failure.descriptor());
    assertRetainedStage(failure, keyFile, true);
  }

  @Test
  void generateDecision_retainsTheStageWhenTheFinalLinkReportsACallerPathViolation() {
    Path keyFile = tempDirectory.resolve("link-path-violation.book-key");
    SqliteCallerPathContractException pathFailure =
        new SqliteCallerPathContractException(
            keyFile,
            SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
            "injected final-link path violation");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                (finalPath, stagedPath) -> {
                  throw pathFailure;
                },
                ignored -> {})
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertRetainedStage(failure, keyFile, false);
  }

  @Test
  void generateDecision_refusesRetiredWitnessResidueBeforeStagingNewSecretMaterial()
      throws Exception {
    Path keyFile = tempDirectory.resolve("legacy-witness-residue.book-key");
    Files.writeString(
        tempDirectory.resolve(".fingrind-no-replace-probe-abandoned"), "retired witness probe");

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteBookKeyFileGenerator.generateDecision(keyFile));

    assertTrue(
        java.util.Objects.requireNonNull(failure.getMessage(), "witness failure message")
            .contains("publication witness"));
    assertInstanceOf(
        SqlitePublicationCapabilityWitness.AcquisitionFailure.class, failure.getCause());
    assertFalse(Files.exists(keyFile));
    try (Stream<Path> siblings = Files.list(tempDirectory)) {
      assertFalse(
          siblings.anyMatch(
              path -> path.getFileName().toString().startsWith(".fingrind-generated-book-key-")));
    }
  }

  @Test
  void generateDecision_mapsAnUnsupportedNoReplacePrimitiveToTheSelectedKeyTarget()
      throws Exception {
    Path keyFile = tempDirectory.resolve("unsupported-no-replace.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                (finalPath, stagedPath) -> {
                  throw new UnsupportedOperationException("no-replace link is unavailable");
                },
                ignored -> {})
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertFalse(Files.exists(keyFile));
  }

  @Test
  void generateIntoExistingOwnedStage_requiresPriorOwnerOnlyCreationWithoutRepairingIt()
      throws Exception {
    assumeTrue(supportsPosix(tempDirectory), "the host filesystem must expose POSIX permissions");
    Path insecureStage = tempDirectory.resolve("insecure-maintenance-stage.tmp");
    Files.writeString(insecureStage, "sentinel", StandardCharsets.UTF_8);
    Set<PosixFilePermission> insecurePermissions =
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ);
    Files.setPosixFilePermissions(insecureStage, insecurePermissions);

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookKeyFileGenerator.generateIntoExistingOwnedStage(insecureStage));

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, exception.failure().descriptor());
    assertEquals("sentinel", Files.readString(insecureStage, StandardCharsets.UTF_8));
    assertEquals(insecurePermissions, Files.getPosixFilePermissions(insecureStage));
  }

  @Test
  void generateIntoExistingOwnedStage_writesOneAlreadyAtomicallyPrivateStage() throws Exception {
    Path finalPath = tempDirectory.resolve("maintenance-final.book-key");
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(finalPath, ".maintenance-key-", ".tmp");
    try {
      SqliteBookKeyFileGenerator.generateIntoExistingOwnedStage(stage.stagedPath());

      assertTrue(Files.isRegularFile(stage.stagedPath()));
      String generatedSecret = Files.readString(stage.stagedPath(), StandardCharsets.UTF_8);
      assertTrue(generatedSecret.matches("[A-Za-z0-9_-]{43}"));
      assertDoesNotThrow(() -> SqliteBookKeyFile.load(stage.stagedPath()).close());
    } finally {
      stage.releaseRetained();
    }
  }

  @Test
  void generateIntoExistingOwnedStage_refusesZeroProgressWhileWritingSecretMaterial() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\keys");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath stage = fileSystem.path("\\keys\\maintenance-stage.tmp");
      stage.exists = true;
      stage.regularFile = true;
      stage.posixPermissions =
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      stage.returnZeroProgressFromNextWrite();

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileGenerator.generateIntoExistingOwnedStage(stage));

      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains("Failed to generate the FinGrind maintenance key stage"));
      assertInstanceOf(IOException.class, failure.getCause());
    }
  }

  @Test
  void generate_rejectsAnAbsentParentDirectoryWithoutCreatingIt() {
    Path keyFile = tempDirectory.resolve("absent-parent").resolve("acme.book-key");

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class, () -> SqliteBookKeyFileGenerator.generate(keyFile));

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, exception.failure().descriptor());
    assertTrue(exception.failure().message().contains("parent directory"));
    assertFalse(Files.exists(keyFile.getParent()));
  }

  @Test
  void generateDecision_returnsTheSecureParentRefusalRatherThanThrowingIt() throws Exception {
    assumeTrue(supportsPosix(tempDirectory), "the host filesystem must expose POSIX permissions");
    Path sharedParent = Files.createDirectory(tempDirectory.resolve("shared-key-parent"));
    Files.setPosixFilePermissions(
        sharedParent,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE));

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(sharedParent.resolve("acme.book-key"))
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertTrue(failure.message().contains("parent directory must use owner-only permissions"));
  }

  @Test
  void generate_rejectsExistingKeyFilesBeforeCreatingAnotherStage() throws Exception {
    Path keyFile = tempDirectory.resolve("existing.book-key");
    Files.writeString(keyFile, "existing", StandardCharsets.UTF_8);

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class, () -> SqliteBookKeyFileGenerator.generate(keyFile));

    assertEquals(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, exception.failure().descriptor());
    try (Stream<Path> siblings = Files.list(tempDirectory)) {
      assertFalse(
          siblings.anyMatch(
              path -> path.getFileName().toString().startsWith(".fingrind-generated-book-key-")));
    }
  }

  @Test
  void generateDecision_rejectsOneRootTargetWithoutAttemptingStageCreation() {
    ContractDecision<GeneratedBookKeyFile> decision =
        SqliteBookKeyFileGenerator.generateDecision(Path.of("/"));

    ContractFailureException exception =
        assertThrows(ContractFailureException.class, decision::requireAccepted);
    assertEquals(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, exception.failure().descriptor());
  }

  @Test
  void generate_rejectsKeyPathWhoseParentResolvesToAFile() throws Exception {
    Path blockingParent = tempDirectory.resolve("blocking-parent");
    Files.writeString(blockingParent, "not-a-directory", StandardCharsets.UTF_8);
    Path nestedKeyFile = blockingParent.resolve("entity.book-key");

    ContractFailureException exception =
        assertThrows(
            ContractFailureException.class,
            () -> SqliteBookKeyFileGenerator.generate(nestedKeyFile));

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, exception.failure().descriptor());
    assertTrue(exception.failure().message().contains("non-directory entry or symlink"));
    assertFalse(Files.exists(nestedKeyFile));
  }

  @Test
  void generateDecision_rejectsUnsupportedSecureFilesystemsAsInvalidBookKeyFiles()
      throws Exception {
    Path zipArchive = tempDirectory.resolve("zipfs-generate-book-key.zip");
    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + zipArchive.toUri()), Map.of("create", "true"))) {
      Path unsupportedPath = zipFileSystem.getPath("/keys/acme.book-key");

      ContractFailureException exception =
          assertThrows(
              ContractFailureException.class,
              () -> SqliteBookKeyFileGenerator.generateDecision(unsupportedPath).requireAccepted());

      assertEquals(
          ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, exception.failure().descriptor());
      assertTrue(exception.failure().message().contains("supports POSIX owner-only permissions"));
    }
  }

  @Test
  void generateDecision_refusesAclOnlyStageCreationRatherThanCreatingThenRepairingAcl()
      throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      AclFixturePath parentPath = fileSystem.path("\\keys");
      parentPath.exists = true;
      parentPath.regularFile = false;
      java.util.Objects.requireNonNull(parentPath.aclView)
          .setAcl(
              java.util.List.of(
                  java.nio.file.attribute.AclEntry.newBuilder()
                      .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                      .setPrincipal(fileSystem.owner)
                      .setPermissions(
                          java.nio.file.attribute.AclEntryPermission.LIST_DIRECTORY,
                          java.nio.file.attribute.AclEntryPermission.ADD_FILE,
                          java.nio.file.attribute.AclEntryPermission.EXECUTE)
                      .build()));
      ContractFailure failure =
          SqliteBookKeyFileGenerator.generateDecision(fileSystem.path("\\keys\\acme.book-key"))
              .requireRejected();

      assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    }
  }

  @Test
  void helperBoundaries_enforceSecureFilesystemAndParentContracts() throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(
                tempDirectory.resolve("ok.book-key")));
    SqliteCallerPathContractException missingParentDirectoryException =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(
                    tempDirectory.resolve("missing").resolve("key")));
    assertEquals(
        SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
        missingParentDirectoryException.pathFailure());
  }

  private static void assertRetainedStage(
      ContractFailure failure, Path finalPath, boolean finalPathExpected) {
    Path retainedStage =
        java.util.Objects.requireNonNull(failure.retainedStage(), "retained stage")
            .retainedStagePath();
    assertTrue(Files.isRegularFile(retainedStage));
    assertEquals(finalPath.toAbsolutePath().normalize().getParent(), retainedStage.getParent());
    assertEquals(finalPathExpected, Files.exists(finalPath));
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }
}
