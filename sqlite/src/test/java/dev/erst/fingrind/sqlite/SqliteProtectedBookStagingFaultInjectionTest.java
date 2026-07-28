package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verifies each staging fault retains created evidence without mutating final destinations. */
class SqliteProtectedBookStagingFaultInjectionTest extends SqliteArtifactPublicationTestSupport {

  @ParameterizedTest(name = "backup: {0}")
  @MethodSource("backupStagingCheckpoints")
  void stageBackupPairFailureAtEveryBoundaryRetainsCreatedStages(
      String checkpointName,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      boolean expectedSecretStage)
      throws Exception {
    SourceBook source = initializedSourceBook("backup-" + checkpointName);
    byte[] sourceBookBefore = Files.readAllBytes(source.bookPath());
    byte[] sourceKeyBefore = Files.readAllBytes(source.keyPath());
    Path backupBookPath =
        tempDirectory.resolve("backup-" + checkpointName).resolve("backup.sqlite");
    Path backupKeyPath = tempDirectory.resolve("backup-" + checkpointName).resolve("backup.key");

    assertStagingFailsAt(
        checkpoint,
        () ->
            SqliteProtectedBookBackupStaging.stageResolvedPair(
                source.bookPath(),
                backupBookPath,
                backupKeyPath,
                SqliteBookKeyFile.load(source.keyPath()),
                VERIFICATION_SUPPORT,
                failAt(checkpoint),
                SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage));

    assertArrayEquals(sourceBookBefore, Files.readAllBytes(source.bookPath()));
    assertArrayEquals(sourceKeyBefore, Files.readAllBytes(source.keyPath()));
    assertFalse(Files.exists(backupBookPath));
    assertFalse(Files.exists(backupKeyPath));
    assertRetainedStageRecord(backupBookPath, true);
    assertRetainedStageRecord(backupKeyPath, expectedSecretStage);
  }

  @ParameterizedTest(name = "restore: {0}")
  @MethodSource("restoreStagingCheckpoints")
  void stageRestoredBookPairFailureAtEveryBoundaryRetainsOnlyCreatedStages(
      String checkpointName,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      boolean expectedBookStage,
      boolean expectedSecretStage)
      throws Exception {
    SourceBook source = initializedSourceBook("restore-" + checkpointName);
    byte[] sourceBookBefore = Files.readAllBytes(source.bookPath());
    byte[] sourceKeyBefore = Files.readAllBytes(source.keyPath());
    Path restoredBookPath =
        writeArtifact("restore-" + checkpointName + "/restored.sqlite", "existing-live-book");
    byte[] restoredBookBefore = Files.readAllBytes(restoredBookPath);
    Path restoredKeyPath =
        tempDirectory.resolve("restore-" + checkpointName).resolve("restored.key");

    assertStagingFailsAt(
        checkpoint,
        () ->
            SqliteProtectedBookRestoreStaging.stageResolvedPair(
                source.bookPath(),
                restoredBookPath,
                restoredKeyPath,
                RestoredBookTargetPolicy.REPLACE_SELECTED,
                SqliteBookKeyFile.load(source.keyPath()),
                VERIFICATION_SUPPORT,
                failAt(checkpoint),
                SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage));

    assertArrayEquals(sourceBookBefore, Files.readAllBytes(source.bookPath()));
    assertArrayEquals(sourceKeyBefore, Files.readAllBytes(source.keyPath()));
    assertArrayEquals(restoredBookBefore, Files.readAllBytes(restoredBookPath));
    assertFalse(Files.exists(restoredKeyPath));
    assertRetainedStageRecord(restoredBookPath, expectedBookStage);
    assertRetainedStageRecord(restoredKeyPath, expectedSecretStage);
  }

  @ParameterizedTest(name = "missing source: {0}")
  @MethodSource("missingSourceOperations")
  void missingSourcePreservesOnlyStagesAllocatedBeforeItsFailure(
      String operation,
      SqliteProtectedBookStagingCheckpoint expectedCheckpoint,
      boolean expectedBookStage,
      MissingSourceStagingOperation stagingOperation)
      throws Exception {
    Path missingSource = tempDirectory.resolve(operation).resolve("missing.sqlite");
    Path bookTarget = tempDirectory.resolve(operation).resolve("target.sqlite");
    Path keyTarget = tempDirectory.resolve(operation).resolve("target.key");
    Path targetParent = java.util.Objects.requireNonNull(bookTarget.getParent(), "target parent");
    Files.createDirectories(targetParent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(targetParent);

    MaintenanceFailure failure;
    try (SqliteBookPassphrase sourcePassphrase = testPassphrase()) {
      failure = stagingOperation.stage(missingSource, bookTarget, keyTarget, sourcePassphrase);
    }

    assertEquals(expectedCheckpoint.failureMessage(), failure.message());
    assertFalse(Files.exists(bookTarget));
    assertFalse(Files.exists(keyTarget));
    assertRetainedStageRecord(bookTarget, expectedBookStage);
    assertRetainedStageRecord(keyTarget, false);
  }

  @Test
  void backupSecretContractFailuresPreserveTheirExactFailureAndReleaseCreatedStages()
      throws Exception {
    SourceBook source = initializedSourceBook("backup-secret-contract-failure");
    Path backupBookPath =
        tempDirectory.resolve("backup-secret-contract-failure").resolve("backup.sqlite");
    Path backupKeyPath =
        tempDirectory.resolve("backup-secret-contract-failure").resolve("backup.key");
    ContractFailureException expected =
        new ContractFailureException(
            ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
                "injected backup secret contract failure", null, null));

    try (SqliteBookPassphrase sourcePassphrase = SqliteBookKeyFile.load(source.keyPath())) {
      assertSame(
          expected,
          assertThrows(
              ContractFailureException.class,
              () ->
                  SqliteProtectedBookBackupStaging.stageResolvedPair(
                      source.bookPath(),
                      backupBookPath,
                      backupKeyPath,
                      sourcePassphrase,
                      VERIFICATION_SUPPORT,
                      checkpoint -> {},
                      ignored -> {
                        throw expected;
                      })));
    }

    assertFalse(Files.exists(backupBookPath));
    assertFalse(Files.exists(backupKeyPath));
    assertRetainedStageRecord(backupBookPath, true);
  }

  @Test
  void unreservedBackupStagingCreatesAnIndependentlyVerifiableRetainedPair() throws Exception {
    SourceBook source = initializedSourceBook("unreserved-backup-success");
    Path backupBookPath =
        tempDirectory.resolve("unreserved-backup-success").resolve("backup.sqlite");
    Path backupKeyPath =
        tempDirectory.resolve("unreserved-backup-success").resolve("backup.key");

    try (SqliteBookPassphrase sourcePassphrase = SqliteBookKeyFile.load(source.keyPath());
        StagedBackupPair stagedBackup =
            SqliteProtectedBookBackupStaging.stageResolvedPair(
                    source.bookPath(),
                    backupBookPath,
                    backupKeyPath,
                    sourcePassphrase,
                    VERIFICATION_SUPPORT,
                    checkpoint -> {},
                    SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage)
                .fold(
                    accepted -> accepted,
                    failure -> {
                      throw new AssertionError(
                          "Expected independently staged backup success: " + failure.message());
                    })) {
      assertFalse(stagedBackup.snapshot().length == 0);
      assertFalse(Files.exists(backupBookPath));
      assertFalse(Files.exists(backupKeyPath));
    }

    assertRetainedStageRecord(backupBookPath, true);
    assertRetainedStageRecord(backupKeyPath, true);
  }

  @Test
  void restoreSecretContractFailuresPreserveTheirExactFailureAndReleaseCreatedStages()
      throws Exception {
    SourceBook source = initializedSourceBook("restore-secret-contract-failure");
    Path restoredBookPath =
        tempDirectory.resolve("restore-secret-contract-failure").resolve("restored.sqlite");
    Path restoredKeyPath =
        tempDirectory.resolve("restore-secret-contract-failure").resolve("restored.key");
    ContractFailureException expected =
        new ContractFailureException(
            ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
                "injected restore secret contract failure", null, null));

    try (SqliteBookPassphrase sourcePassphrase = SqliteBookKeyFile.load(source.keyPath())) {
      assertSame(
          expected,
          assertThrows(
              ContractFailureException.class,
              () ->
                  SqliteProtectedBookRestoreStaging.stageResolvedPair(
                      source.bookPath(),
                      restoredBookPath,
                      restoredKeyPath,
                      RestoredBookTargetPolicy.REQUIRE_ABSENT,
                      sourcePassphrase,
                      VERIFICATION_SUPPORT,
                      checkpoint -> {},
                      ignored -> {
                        throw expected;
                      })));
    }

    assertFalse(Files.exists(restoredBookPath));
    assertFalse(Files.exists(restoredKeyPath));
    assertRetainedStageRecord(restoredBookPath, true);
  }

  @Test
  void unreservedRestoreStagingCreatesAnIndependentlyVerifiableRetainedPair() throws Exception {
    SourceBook source = initializedSourceBook("unreserved-restore-success");
    Path restoredBookPath =
        tempDirectory.resolve("unreserved-restore-success").resolve("restored.sqlite");
    Path restoredKeyPath =
        tempDirectory.resolve("unreserved-restore-success").resolve("restored.key");

    try (SqliteBookPassphrase sourcePassphrase = SqliteBookKeyFile.load(source.keyPath());
        StagedRestoredBookPair stagedRestore =
            SqliteProtectedBookRestoreStaging.stageResolvedPair(
                    source.bookPath(),
                    restoredBookPath,
                    restoredKeyPath,
                    RestoredBookTargetPolicy.REQUIRE_ABSENT,
                    sourcePassphrase,
                    VERIFICATION_SUPPORT,
                    checkpoint -> {},
                    SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage)
                .fold(
                    accepted -> accepted,
                    failure -> {
                      throw new AssertionError(
                          "Expected independently staged restore success: " + failure.message());
                    })) {
      assertFalse(Files.exists(restoredBookPath));
      assertFalse(Files.exists(restoredKeyPath));
    }

    assertRetainedStageRecord(restoredBookPath, true);
    assertRetainedStageRecord(restoredKeyPath, true);
  }

  private static Stream<Arguments> backupStagingCheckpoints() {
    return Stream.of(
        Arguments.of("export", SqliteProtectedBookStagingCheckpoint.BACKUP_EXPORT, false),
        Arguments.of(
            "secret-generation",
            SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
            true),
        Arguments.of("rekey", SqliteProtectedBookStagingCheckpoint.BACKUP_REKEY, true));
  }

  private static Stream<Arguments> restoreStagingCheckpoints() {
    return Stream.of(
        Arguments.of("copy", SqliteProtectedBookStagingCheckpoint.RESTORE_COPY, false, false),
        Arguments.of(
            "secret-generation",
            SqliteProtectedBookStagingCheckpoint.RESTORE_SECRET_GENERATION,
            true,
            true),
        Arguments.of("rekey", SqliteProtectedBookStagingCheckpoint.RESTORE_REKEY, true, true));
  }

  private static Stream<Arguments> missingSourceOperations() {
    return Stream.of(
        Arguments.of(
            "backup",
            SqliteProtectedBookStagingCheckpoint.BACKUP_SOURCE_OPEN,
            true,
            (MissingSourceStagingOperation)
                (source, bookTarget, keyTarget, passphrase) ->
                    failedValue(
                        SqliteProtectedBookBackupStaging.stageResolvedPair(
                            source,
                            bookTarget,
                            keyTarget,
                            passphrase,
                            VERIFICATION_SUPPORT,
                            checkpoint -> {},
                            SqliteBookKeyFileGenerator::generate))),
        Arguments.of(
            "restore",
            SqliteProtectedBookStagingCheckpoint.RESTORE_COPY,
            false,
            (MissingSourceStagingOperation)
                (source, bookTarget, keyTarget, passphrase) ->
                    failedValue(
                        SqliteProtectedBookRestoreStaging.stageResolvedPair(
                            source,
                            bookTarget,
                            keyTarget,
                            RestoredBookTargetPolicy.REQUIRE_ABSENT,
                            passphrase,
                            VERIFICATION_SUPPORT,
                            checkpoint -> {},
                            SqliteBookKeyFileGenerator::generate))));
  }

  private SourceBook initializedSourceBook(String directoryName) {
    Path sourceBookPath = tempDirectory.resolve(directoryName).resolve("source.sqlite");
    BookAccess sourceAccess = bookAccess(sourceBookPath);
    initializeBook(sourceAccess);
    return new SourceBook(sourceBookPath, keyFilePath(sourceAccess));
  }

  private static void assertStagingFailsAt(
      SqliteProtectedBookStagingCheckpoint checkpoint,
      java.util.function.Supplier<? extends MaintenanceDecision<?>> stagingOperation) {
    MaintenanceFailure failure =
        stagingOperation
            .get()
            .fold(
                accepted -> {
                  throw new AssertionError("Expected staging failure at " + checkpoint + ".");
                },
                failed -> failed);
    assertEquals(checkpoint.failureMessage(), failure.message());
  }

  private static SqliteProtectedBookStagingCheckpointListener failAt(
      SqliteProtectedBookStagingCheckpoint expectedCheckpoint) {
    return checkpoint -> {
      if (checkpoint == expectedCheckpoint) {
        throw new InjectedStagingFailure();
      }
    };
  }

  private static void assertRetainedStageRecord(Path finalPath, boolean expected) {
    assertEquals(expected, !SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }

  private static Path keyFilePath(BookAccess bookAccess) {
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected one key-file-backed test access tuple.");
    };
  }

  private static SqliteBookPassphrase testPassphrase() {
    return SqliteBookPassphrase.fromUtf8Bytes(
        "staging fault injection", TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8));
  }

  private record SourceBook(Path bookPath, Path keyPath) {}

  /** Stages one deliberately absent source through a publication workflow. */
  @FunctionalInterface
  private interface MissingSourceStagingOperation {
    MaintenanceFailure stage(
        Path source, Path bookTarget, Path keyTarget, SqliteBookPassphrase passphrase);
  }

  /** Deterministic fault used to prove that retained publication artifacts remain recoverable. */
  private static final class InjectedStagingFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
