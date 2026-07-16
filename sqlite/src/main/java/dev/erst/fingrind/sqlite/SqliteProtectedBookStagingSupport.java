package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared SQLite staging helpers for protected-book maintenance flows. */
final class SqliteProtectedBookStagingSupport {
  /** Observes one staging boundary before its operation mutates the staged artifacts. */
  @FunctionalInterface
  interface StagingCheckpointListener {
    /** Observes one named staging boundary. */
    void reached(StagingCheckpoint checkpoint);
  }

  /** Staging boundaries whose failures must leave final artifacts untouched. */
  enum StagingCheckpoint {
    BACKUP_EXPORT("Failed to prepare the encrypted FinGrind backup stage."),
    BACKUP_SOURCE_OPEN("Failed to open the encrypted FinGrind backup source."),
    BACKUP_STAGE_OPEN("Failed to open the encrypted FinGrind backup stage."),
    BACKUP_COPY("Failed to copy the encrypted FinGrind backup stage."),
    BACKUP_HARDEN("Failed to secure the encrypted FinGrind backup stage."),
    BACKUP_SECRET_GENERATION("Failed to generate the FinGrind backup stage key."),
    BACKUP_REKEY("Failed to re-encrypt the FinGrind backup stage."),
    RESTORE_COPY("Failed to copy the encrypted FinGrind restored-book stage."),
    RESTORE_SECRET_GENERATION("Failed to generate the FinGrind restored-book stage key."),
    RESTORE_REKEY("Failed to re-encrypt the FinGrind restored-book stage.");

    private final String failureMessage;

    StagingCheckpoint(String failureMessage) {
      this.failureMessage = failureMessage;
    }

    String failureMessage() {
      return failureMessage;
    }
  }

  private static final StagingCheckpointListener NO_OP_STAGING_CHECKPOINT_LISTENER =
      checkpoint -> {};

  private SqliteProtectedBookStagingSupport() {}

  static MaintenanceDecision<StagedBackupPair> stageResolvedBackupPair(
      Path normalizedBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageResolvedBackupPair(
        normalizedBookPath,
        preparedPublication.bookTargetPath(),
        preparedPublication.secretTargetPath(),
        sourcePassphrase,
        verificationSupport,
        NO_OP_STAGING_CHECKPOINT_LISTENER,
        SqliteBookKeyFileGenerator::generate,
        preparedPublication);
  }

  static MaintenanceDecision<StagedBackupPair> stageResolvedBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      StagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator) {
    SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(normalizedBackupFilePath);
    SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
        normalizedBackupBookKeyFilePath);
    recoverUnreservedStageTargets(normalizedBackupFilePath, normalizedBackupBookKeyFilePath);
    SqliteGeneratedSecretTarget.requireAbsent(normalizedBackupBookKeyFilePath);
    return stageResolvedBackupPair(
        normalizedBookPath,
        normalizedBackupFilePath,
        normalizedBackupBookKeyFilePath,
        sourcePassphrase,
        verificationSupport,
        checkpointListener,
        stagedSecretGenerator,
        null);
  }

  static MaintenanceDecision<StagedBackupPair> stageResolvedBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      StagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator,
      @Nullable SqlitePreparedPairPublication preparedPublication) {
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Objects.requireNonNull(stagedSecretGenerator, "stagedSecretGenerator");
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(
          normalizedBackupFilePath);
      SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
          normalizedBackupBookKeyFilePath);
      SqliteOwnedStagedArtifact stagedBackupFile =
          preparedPublication == null
              ? SqliteOwnedStagedArtifact.create(normalizedBackupFilePath, ".backup-", ".sqlite")
              : preparedPublication.createBookStage(".backup-", ".sqlite");
      SqliteOwnedStagedArtifact stagedBackupBookKeyFile =
          preparedPublication == null
              ? SqliteOwnedStagedArtifact.create(
                  normalizedBackupBookKeyFilePath, ".backup-key-", ".tmp")
              : preparedPublication.createSecretStage(".backup-key-", ".tmp");
      SqliteStagedBackupPair stagedBackupPair;
      StagingCheckpoint activeCheckpoint = StagingCheckpoint.BACKUP_EXPORT;
      try {
        checkpointListener.reached(StagingCheckpoint.BACKUP_EXPORT);
        SqliteProtectedBookStagingFiles.exportBackupUsingSqlite(
            normalizedBookPath, stagedBackupFile.stagedPath(), sourceLease.passphrase().copy());
        activeCheckpoint = StagingCheckpoint.BACKUP_SECRET_GENERATION;
        try (SqliteBookPassphrase stagedBackupPassphrase =
            SqliteDistinctStagedSecret.generate(
                stagedBackupBookKeyFile.stagedPath(),
                sourceLease.passphrase(),
                StagingCheckpoint.BACKUP_SECRET_GENERATION,
                checkpointListener,
                stagedSecretGenerator)) {
          activeCheckpoint = StagingCheckpoint.BACKUP_REKEY;
          checkpointListener.reached(StagingCheckpoint.BACKUP_REKEY);
          SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
              stagedBackupFile.stagedPath(),
              sourceLease.passphrase().copy(),
              stagedBackupPassphrase.copy());
          SqlitePreparedPairPublication.@Nullable PublicationReservations reservations =
              preparedPublication == null ? null : preparedPublication.transferReservations();
          stagedBackupPair =
              new SqliteStagedBackupPair(
                  new SqliteStagedProtectedBookPairArtifacts(
                      stagedBackupFile,
                      normalizedBackupFilePath,
                      stagedBackupBookKeyFile,
                      normalizedBackupBookKeyFilePath),
                  stagedBackupPassphrase.utf8BytesCopy(),
                  verificationSupport,
                  Files::createLink,
                  Files::createLink,
                  reservations == null ? null : reservations.bookReservation(),
                  reservations == null ? null : reservations.secretReservation());
        }
        return MaintenanceDecision.accepted(stagedBackupPair);
      } catch (SqliteProtectedBookStagingFiles.BackupExportFailure failure) {
        SqliteOwnedStagedArtifact.discardAll(stagedBackupFile, stagedBackupBookKeyFile);
        return stagingFailure(normalizedBackupFilePath, "backupFilePath", failure.checkpoint());
      } catch (RuntimeException exception) {
        SqliteOwnedStagedArtifact.discardAll(stagedBackupFile, stagedBackupBookKeyFile);
        return stagingFailure(normalizedBackupFilePath, "backupFilePath", activeCheckpoint);
      }
    } finally {
      sourceLease.wipe();
    }
  }

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedRestoredBookPair(
      Path normalizedSourceBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageResolvedRestoredBookPair(
        normalizedSourceBookPath,
        preparedPublication.bookTargetPath(),
        preparedPublication.secretTargetPath(),
        preparedPublication.bookTargetPolicy(),
        sourcePassphrase,
        verificationSupport,
        NO_OP_STAGING_CHECKPOINT_LISTENER,
        SqliteBookKeyFileGenerator::generate,
        preparedPublication);
  }

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedRestoredBookPair(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      Path normalizedBookKeyFilePath,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      StagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator) {
    SqliteBookFileSecurity.requireSupportedSecureFilesystem(normalizedBookFilePath);
    SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedBookKeyFilePath);
    SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(normalizedBookFilePath);
    SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
        normalizedBookKeyFilePath);
    recoverUnreservedStageTargets(normalizedBookFilePath, normalizedBookKeyFilePath);
    SqliteGeneratedSecretTarget.requireAbsent(normalizedBookKeyFilePath);
    return stageResolvedRestoredBookPair(
        normalizedSourceBookPath,
        normalizedBookFilePath,
        normalizedBookKeyFilePath,
        targetPolicy,
        sourcePassphrase,
        verificationSupport,
        checkpointListener,
        stagedSecretGenerator,
        null);
  }

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedRestoredBookPair(
      Path normalizedSourceBookPath,
      Path normalizedBookFilePath,
      Path normalizedBookKeyFilePath,
      RestoredBookTargetPolicy targetPolicy,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      StagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator,
      @Nullable SqlitePreparedPairPublication preparedPublication) {
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Objects.requireNonNull(stagedSecretGenerator, "stagedSecretGenerator");
    @Nullable SqliteOwnedStagedArtifact stagedBookFile = null;
    @Nullable SqliteOwnedStagedArtifact stagedBookKeyFile = null;
    SqliteStagedRestoredBookPair stagedRestoredBookPair;
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      SqliteBookFileSecurity.requireSupportedSecureFilesystem(normalizedBookFilePath);
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(normalizedBookKeyFilePath);
      SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(normalizedBookFilePath);
      SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(
          normalizedBookKeyFilePath);
      stagedBookFile =
          preparedPublication == null
              ? SqliteOwnedStagedArtifact.create(normalizedBookFilePath, ".restore-", ".tmp")
              : preparedPublication.createBookStage(".restore-", ".tmp");
      stagedBookKeyFile =
          preparedPublication == null
              ? SqliteOwnedStagedArtifact.create(normalizedBookKeyFilePath, ".restore-key-", ".tmp")
              : preparedPublication.createSecretStage(".restore-key-", ".tmp");
      StagingCheckpoint activeCheckpoint = StagingCheckpoint.RESTORE_COPY;
      try {
        checkpointListener.reached(activeCheckpoint);
        Files.copy(
            normalizedSourceBookPath,
            stagedBookFile.stagedPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES);
        SqliteProtectedBookStagingFiles.hardenBookArtifacts(stagedBookFile.stagedPath());
        activeCheckpoint = StagingCheckpoint.RESTORE_SECRET_GENERATION;
        try (SqliteBookPassphrase restoredPassphrase =
            SqliteDistinctStagedSecret.generate(
                stagedBookKeyFile.stagedPath(),
                sourceLease.passphrase(),
                activeCheckpoint,
                checkpointListener,
                stagedSecretGenerator)) {
          activeCheckpoint = StagingCheckpoint.RESTORE_REKEY;
          checkpointListener.reached(activeCheckpoint);
          SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
              stagedBookFile.stagedPath(),
              sourceLease.passphrase().copy(),
              restoredPassphrase.copy());
          SqlitePreparedPairPublication.@Nullable PublicationReservations reservations =
              preparedPublication == null ? null : preparedPublication.transferReservations();
          stagedRestoredBookPair =
              SqliteStagedRestoredBookPairFactory.create(
                  new SqliteStagedProtectedBookPairArtifacts(
                      stagedBookFile,
                      normalizedBookFilePath,
                      stagedBookKeyFile,
                      normalizedBookKeyFilePath),
                  targetPolicy,
                  restoredPassphrase.utf8BytesCopy(),
                  verificationSupport,
                  new SqliteRestoredBookPairPublication.Operators(
                      Files::createLink,
                      Files::createLink,
                      SqliteProtectedBookPublicationSupport::moveReplacing),
                  reservations == null ? null : reservations.bookReservation(),
                  reservations == null ? null : reservations.secretReservation());
        }
        return MaintenanceDecision.accepted(stagedRestoredBookPair);
      } catch (IOException | RuntimeException exception) {
        SqliteOwnedStagedArtifact.discardAll(stagedBookFile, stagedBookKeyFile);
        return stagingFailure(normalizedBookFilePath, "bookFilePath", activeCheckpoint);
      }
    } catch (RuntimeException exception) {
      SqliteOwnedStagedArtifact.discardAll(stagedBookFile, stagedBookKeyFile);
      throw exception;
    } finally {
      sourceLease.wipe();
    }
  }

  private static <T> MaintenanceDecision<T> stagingFailure(
      Path artifactPath, String argumentName, StagingCheckpoint checkpoint) {
    return MaintenanceDecision.failed(
        new MaintenanceFailure(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
            checkpoint.failureMessage(),
            "Inspect the selected filesystem path and retry after resolving the underlying storage problem.",
            argumentName,
            ContractFailurePaths.primary(artifactPath)));
  }

  /** Reclaims exact owned stages for direct fixture staging that has no pair reservation. */
  private static void recoverUnreservedStageTargets(Path firstFinalPath, Path secondFinalPath) {
    SqliteOwnedStagedArtifact.recoverFor(firstFinalPath);
    SqliteOwnedStagedArtifact.recoverFor(secondFinalPath);
  }

  /** Resets one generated-secret stage using the supplied file deletion owner. */
  static void resetStagedSecretFile(
      Path stagedSecretFilePath, SqliteProtectedBookPublicationSupport.PathDeleter deleter) {
    SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecretFilePath, deleter);
  }
}
