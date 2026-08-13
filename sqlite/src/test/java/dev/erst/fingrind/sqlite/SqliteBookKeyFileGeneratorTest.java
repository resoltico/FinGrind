package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PublicationCleanupOutcome;
import dev.erst.fingrind.core.PublicationCommitOutcome;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionId;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.PublicationTransactionState;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Tests generated-key admission and delegation to the sole publication transaction owner. */
class SqliteBookKeyFileGeneratorTest {
  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

  @Test
  void generateDecisionDelegatesOneSecretMemberAndReportsOnlyCompletedTransactionEvidence() {
    Path keyFile = tempDirectory.resolve("acme.book-key");
    RecordingTransactions transactions = new RecordingTransactions();

    GeneratedBookKeyFile generated =
        SqliteBookKeyFileGenerator.generateDecision(keyFile, () -> transactions).requireAccepted();

    assertEquals(
        keyFile.toAbsolutePath().normalize(), generated.publication().publishedArtifactPath());
    assertEquals(transactions.success, generated.publication().transactionResult());
    assertEquals("base64url-no-padding", generated.encoding());
    assertEquals(256, generated.entropyBits());
    PublicationTransactionRequest request = transactions.requiredRequest();
    assertEquals(1, request.members().size());
    var member = request.members().getFirst();
    assertEquals("book-key", member.memberId());
    assertEquals(
        dev.erst.fingrind.core.PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY, member.role());
    assertEquals(keyFile.toAbsolutePath().normalize(), member.finalPath());
    assertTrue(member.toString().contains("secretPayload=<redacted>"));
  }

  @Test
  void generateDecisionMapsAnIncompleteTransactionToIdOnlyRecoveryEvidence() {
    Path keyFile = tempDirectory.resolve("incomplete.book-key");
    RecordingTransactions transactions = new RecordingTransactions();
    PublicationTransactionResult incomplete =
        new PublicationTransactionResult(
            new PublicationTransactionId("fedcba9876543210fedcba9876543210"),
            PublicationTransactionState.COMMIT_UNCERTAIN,
            new dev.erst.fingrind.core.PublicationTransactionOutcome(
                PublicationCommitOutcome.COMMIT_UNCERTAIN, PublicationCleanupOutcome.INCOMPLETE));
    transactions.failure =
        new PublicationTransactionExecutionException(incomplete, new IOException("commit unknown"));

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(keyFile, () -> transactions).requireRejected();

    assertEquals(
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE, failure.descriptor());
    assertEquals(null, failure.retainedStage());
    ContractFailureDetails.PublicationTransactionIncomplete details =
        assertInstanceOf(
            ContractFailureDetails.PublicationTransactionIncomplete.class, failure.details());
    assertEquals(keyFile.toAbsolutePath().normalize(), details.candidateArtifactPath());
    assertEquals(incomplete, details.transactionResult());
  }

  @Test
  void generateDecisionMapsNoReplaceCollisionWithoutOpeningARecoveryPath() {
    Path keyFile = tempDirectory.resolve("collision.book-key");
    RecordingTransactions transactions = new RecordingTransactions();
    transactions.failure = new FileAlreadyExistsException(keyFile.toString());

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(keyFile, () -> transactions).requireRejected();

    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, failure.descriptor());
    assertEquals(null, failure.retainedStage());
    assertEquals(
        keyFile.toAbsolutePath().normalize(),
        java.util.Objects.requireNonNull(failure.paths(), "failure paths").path());
  }

  @Test
  void generateDecisionMapsAnInjectedTargetOccupiedRaceWithoutRecoveryEvidence() {
    Path keyFile = tempDirectory.resolve("raced.book-key");
    RecordingTransactions transactions = new RecordingTransactions();
    transactions.runtimeFailure = new SqliteGeneratedSecretTargetOccupiedException(keyFile);

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(keyFile, () -> transactions).requireRejected();

    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, failure.descriptor());
    assertEquals(null, failure.retainedStage());
  }

  @Test
  void generateDecisionMapsTransactionStartupCallerPathRefusals() {
    Path keyFile = tempDirectory.resolve("startup-refusal.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                () -> {
                  throw new SqliteCallerPathContractException(
                      keyFile,
                      SqliteCallerPathFailure.PARENT_PATH_COLLISION,
                      "injected transaction-store refusal");
                })
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
  }

  @Test
  void generateDecisionPreservesUnexpectedTransactionStartupFailures() {
    Path keyFile = tempDirectory.resolve("startup-io.book-key");
    IOException failure = new IOException("injected transaction-store failure");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileGenerator.generateDecision(
                    keyFile,
                    () -> {
                      throw failure;
                    }));

    assertEquals(failure, exception.getCause());
    assertTrue(String.valueOf(exception.getMessage()).contains("publication transaction owner"));
  }

  @Test
  void generateDecisionRejectsExistingTargetBeforeOpeningTheTransactionOwner() throws Exception {
    Path keyFile = tempDirectory.resolve("existing.book-key");
    Files.writeString(keyFile, "existing", StandardCharsets.UTF_8);

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                () -> {
                  throw new AssertionError("An existing target must precede transaction startup.");
                })
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED, failure.descriptor());
    assertEquals("existing", Files.readString(keyFile, StandardCharsets.UTF_8));
  }

  @Test
  void generateDecisionRejectsAnAbsentParentBeforeOpeningTheTransactionOwner() {
    Path keyFile = tempDirectory.resolve("absent").resolve("acme.book-key");

    ContractFailure failure =
        SqliteBookKeyFileGenerator.generateDecision(
                keyFile,
                () -> {
                  throw new AssertionError("An absent parent must precede transaction startup.");
                })
            .requireRejected();

    assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
    assertFalse(Files.exists(keyFile.getParent()));
  }

  @Test
  void generateDecisionRejectsUnsupportedSecureFilesystemsBeforeOpeningTheTransactionOwner()
      throws Exception {
    Path zipArchive = tempDirectory.resolve("unsupported-book-key-filesystem.zip");
    try (FileSystem zipFileSystem =
        FileSystems.newFileSystem(
            URI.create("jar:" + zipArchive.toUri()), Map.of("create", "true"))) {
      Path keyFile = zipFileSystem.getPath("/keys/acme.book-key");

      ContractFailure failure =
          SqliteBookKeyFileGenerator.generateDecision(
                  keyFile,
                  () -> {
                    throw new AssertionError(
                        "Unsupported filesystems must precede transaction startup.");
                  })
              .requireRejected();

      assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
      assertTrue(failure.message().contains("supports POSIX owner-only permissions"));
    }
  }

  @Test
  void generateDecisionPreservesUnexpectedParentSecurityInspectionFailures() throws Exception {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\keys");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      IOException inspectionFailure =
          new IOException("injected POSIX permission inspection failure");
      parent.failPosixReadAttributesWith(inspectionFailure);

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteBookKeyFileGenerator.generateDecision(
                      fileSystem.path("\\keys\\acme.book-key"),
                      () -> {
                        throw new AssertionError(
                            "Parent inspection must precede transaction startup.");
                      }));

      assertEquals(inspectionFailure, exception.getCause());
      assertTrue(String.valueOf(exception.getMessage()).contains("private parent directory"));
    }
  }

  @Test
  @ResourceLock(Resources.SYSTEM_PROPERTIES)
  void generateDecisionMapsCanonicalJournalStoreAdmissionRefusals() throws Exception {
    Path stateHome = tempDirectory.resolve(".local").resolve("state");
    Path applicationHome = stateHome.resolve("fingrind");
    for (Path directory :
        java.util.List.of(tempDirectory.resolve(".local"), stateHome, applicationHome)) {
      Files.createDirectory(directory);
      SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(directory);
    }
    Files.writeString(applicationHome.resolve("publication-transactions"), "collision");
    String originalUserHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDirectory.toString());
    try {
      ContractFailure failure =
          SqliteBookKeyFileGenerator.generateDecision(
                  tempDirectory.resolve("journal-refusal.book-key"),
                  PublicationTransactionPublisher::openCanonical)
              .requireRejected();

      assertEquals(ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE, failure.descriptor());
      assertTrue(
          failure.message().contains("parent must remain an existing real private directory"));
    } finally {
      if (originalUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", originalUserHome);
      }
    }
  }

  @Test
  void generateIntoExistingOwnedStageRequiresPriorOwnerOnlyCreationWithoutRepairingIt()
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
  void generateIntoExistingOwnedStageWritesOneAlreadyAtomicallyPrivateStage() throws Exception {
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
  void generateIntoExistingOwnedStageRefusesZeroProgressWhileWritingSecretMaterial() {
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

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteBookKeyFileGenerator.generateIntoExistingOwnedStage(stage));

      assertTrue(
          String.valueOf(exception.getMessage())
              .contains("Failed to materialize the FinGrind maintenance key stage"));
      assertInstanceOf(IOException.class, exception.getCause());
    }
  }

  @Test
  void createRetainedStageMaterializesOneOwnerOnlyStageForTheRemainingPairWorkflows()
      throws Exception {
    Path keyFile = tempDirectory.resolve("pair-workflow.book-key");
    byte[] material = new byte[] {1, 2, 3};

    ArtifactPublicationRetention retention =
        SqliteBookKeyFileMaterial.createRetainedStage(keyFile, material);

    assertTrue(Files.isRegularFile(retention.retainedStagePath()));
    assertEquals(keyFile.getParent(), retention.retainedStagePath().getParent());
    assertArrayEquals(material, Files.readAllBytes(retention.retainedStagePath()));
  }

  @Test
  void createRetainedStagePreservesExactRetainedStageWriterEvidence() throws Exception {
    Path keyFile = tempDirectory.resolve("retained-stage-writer.book-key");
    SqliteOwnedStagedArtifact stage =
        SqliteOwnedStagedArtifact.create(keyFile, ".retained-stage-writer-", ".tmp");
    try {
      ArtifactPublicationRetainedStageException writerFailure =
          new ArtifactPublicationRetainedStageException(
              new ArtifactPublicationRetention(stage.stagedPath()),
              new IOException("injected retained-stage writer failure"));

      SqliteBookKeyFileRetainedStageMaterializationFailure exception =
          assertThrows(
              SqliteBookKeyFileRetainedStageMaterializationFailure.class,
              () ->
                  SqliteBookKeyFileMaterial.createRetainedStage(
                      keyFile,
                      new byte[] {1, 2, 3},
                      (ignoredParent, ignoredPrefix, ignoredSuffix, ignoredBytes) -> {
                        throw writerFailure;
                      }));

      assertEquals(stage.stagedPath(), exception.retention().retainedStagePath());
      assertEquals(writerFailure, exception.getCause());
    } finally {
      stage.releaseRetained();
    }
  }

  @Test
  void createRetainedStagePreservesUnexpectedPrivateStageWriterFailures() {
    Path keyFile = tempDirectory.resolve("unexpected-stage-writer.book-key");
    IOException writerFailure = new IOException("injected stage writer failure");

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFileMaterial.createRetainedStage(
                    keyFile,
                    new byte[] {1, 2, 3},
                    (ignoredParent, ignoredPrefix, ignoredSuffix, ignoredBytes) -> {
                      throw writerFailure;
                    }));

    assertTrue(String.valueOf(exception.getMessage()).contains("private stage"));
    assertEquals(writerFailure, exception.getCause());
  }

  @Test
  void helperBoundariesCoverSecureFilesystemAndParentContracts() throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(
                tempDirectory.resolve("ok.book-key")));
    SqliteCallerPathContractException missingParent =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(
                    tempDirectory.resolve("missing").resolve("key")));
    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, missingParent.pathFailure());
    Path filesystemRoot =
        java.util.Objects.requireNonNull(
            tempDirectory.toAbsolutePath().getRoot(), "filesystem root");
    SqliteCallerPathContractException rootWithoutParent =
        assertThrows(
            SqliteCallerPathContractException.class,
            () -> SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(filesystemRoot));
    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, rootWithoutParent.pathFailure());
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("acl"))) {
      assertDoesNotThrow(
          () ->
              SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(fileSystem.path("\\key")));
    }
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains("posix");
  }

  /** Captures the direct generated-key publication request without materializing a journal. */
  private static final class RecordingTransactions implements PublicationTransactionService {
    private final PublicationTransactionResult success =
        new PublicationTransactionResult(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            PublicationTransactionState.COMPLETE,
            new dev.erst.fingrind.core.PublicationTransactionOutcome(
                PublicationCommitOutcome.ALL_COMMITTED, PublicationCleanupOutcome.COMPLETE));
    private @Nullable PublicationTransactionRequest request;
    private @Nullable IOException failure;
    private @Nullable RuntimeException runtimeFailure;

    @Override
    public PublicationTransactionResult publish(PublicationTransactionRequest request)
        throws IOException {
      this.request = java.util.Objects.requireNonNull(request, "request");
      if (failure != null) {
        throw failure;
      }
      if (runtimeFailure != null) {
        throw runtimeFailure;
      }
      return success;
    }

    @Override
    public dev.erst.fingrind.core.PublicationTransactionStageReservation reserveStages(
        PublicationTransactionRequest request) {
      throw new AssertionError("Generated key publication must not reserve an external stage.");
    }

    @Override
    public PublicationTransactionResult publishReservedStages(
        dev.erst.fingrind.core.PublicationTransactionStageReservation reservation) {
      throw new AssertionError("Generated key publication must not admit an external stage.");
    }

    @Override
    public PublicationTransactionResult recover(PublicationTransactionId transactionId) {
      throw new AssertionError("Generated key publication must not recover during creation.");
    }

    @Override
    public dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt recoverWithReceipt(
        PublicationTransactionId transactionId) {
      throw new AssertionError("Generated key publication must not recover a transaction receipt.");
    }

    private PublicationTransactionRequest requiredRequest() {
      return java.util.Objects.requireNonNull(request, "request");
    }
  }
}
