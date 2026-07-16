package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
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
    BACKUP_EXPORT,
    BACKUP_SECRET_GENERATION,
    BACKUP_REKEY,
    RESTORE_COPY,
    RESTORE_SECRET_GENERATION,
    RESTORE_REKEY
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
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase;
        SqliteBookPassphrase exportPassphrase = sourcePassphrase.copy()) {
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
      try {
        checkpointListener.reached(StagingCheckpoint.BACKUP_EXPORT);
        exportBackupUsingSqlite(
            normalizedBookPath, stagedBackupFile.stagedPath(), exportPassphrase);
        try (SqliteBookPassphrase stagedBackupPassphrase =
            SqliteDistinctStagedSecret.generate(
                stagedBackupBookKeyFile.stagedPath(),
                sourcePassphrase,
                StagingCheckpoint.BACKUP_SECRET_GENERATION,
                checkpointListener,
                stagedSecretGenerator)) {
          checkpointListener.reached(StagingCheckpoint.BACKUP_REKEY);
          rekeyBookCopy(
              stagedBackupFile.stagedPath(),
              sourcePassphrase.copy(),
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
      } catch (RuntimeException exception) {
        SqliteOwnedStagedArtifact.discardAll(stagedBackupFile, stagedBackupBookKeyFile);
        throw exception;
      }
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
    try (SqliteBookPassphrase ignoredSource = sourcePassphrase) {
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
      checkpointListener.reached(StagingCheckpoint.RESTORE_COPY);
      Files.copy(
          normalizedSourceBookPath,
          stagedBookFile.stagedPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteProtectedBookStagingFiles.hardenBookArtifacts(stagedBookFile.stagedPath());
      try (SqliteBookPassphrase restoredPassphrase =
          SqliteDistinctStagedSecret.generate(
              stagedBookKeyFile.stagedPath(),
              sourcePassphrase,
              StagingCheckpoint.RESTORE_SECRET_GENERATION,
              checkpointListener,
              stagedSecretGenerator)) {
        checkpointListener.reached(StagingCheckpoint.RESTORE_REKEY);
        rekeyBookCopy(
            stagedBookFile.stagedPath(), sourcePassphrase.copy(), restoredPassphrase.copy());
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
    } catch (IOException exception) {
      SqliteOwnedStagedArtifact.discardAll(stagedBookFile, stagedBookKeyFile);
      throw new IllegalStateException(
          "Failed to stage the restored FinGrind live-book pair for "
              + SqliteMachinePaths.absoluteValue(normalizedBookFilePath)
              + ".",
          exception);
    } catch (RuntimeException exception) {
      SqliteOwnedStagedArtifact.discardAll(stagedBookFile, stagedBookKeyFile);
      throw exception;
    }
  }

  /** Reclaims exact owned stages for direct fixture staging that has no pair reservation. */
  private static void recoverUnreservedStageTargets(Path firstFinalPath, Path secondFinalPath) {
    SqliteOwnedStagedArtifact.recoverFor(firstFinalPath);
    SqliteOwnedStagedArtifact.recoverFor(secondFinalPath);
  }

  static void exportBackupUsingSqlite(
      Path normalizedBookPath, Path stagedBackupFilePath, SqliteBookPassphrase sourcePassphrase) {
    SqliteProtectedBookStagingFiles.exportBackupUsingSqlite(
        normalizedBookPath, stagedBackupFilePath, sourcePassphrase);
  }

  static void rekeyBookCopy(
      Path normalizedBookPath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteBookPassphrase replacementPassphrase) {
    SqliteProtectedBookStagingFiles.rekeyBookCopy(
        normalizedBookPath, sourcePassphrase, replacementPassphrase);
  }

  /** Resets one generated-secret stage using the supplied file deletion owner. */
  static void resetStagedSecretFile(
      Path stagedSecretFilePath, SqliteProtectedBookPublicationSupport.PathDeleter deleter) {
    SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecretFilePath, deleter);
  }
}
