package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Creates an independently encrypted staged backup pair before publication. */
final class SqliteProtectedBookBackupStaging {
  private SqliteProtectedBookBackupStaging() {}

  static MaintenanceDecision<StagedBackupPair> stageResolvedPair(
      Path normalizedBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageResolvedPair(
        normalizedBookPath,
        preparedPublication.bookTargetPath(),
        preparedPublication.secretTargetPath(),
        sourcePassphrase,
        verificationSupport,
        SqliteProtectedBookStagingCheckpointListener.none(),
        SqliteBookKeyFileGenerator::generateIntoExistingOwnedStage,
        preparedPublication);
  }

  static MaintenanceDecision<StagedBackupPair> stageResolvedPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator) {
    SqliteProtectedBookStagingTargetPreparation.prepareUnreservedBackupTargets(
        normalizedBackupFilePath, normalizedBackupBookKeyFilePath);
    return stageResolvedPair(
        normalizedBookPath,
        normalizedBackupFilePath,
        normalizedBackupBookKeyFilePath,
        sourcePassphrase,
        verificationSupport,
        checkpointListener,
        stagedSecretGenerator,
        null);
  }

  static MaintenanceDecision<StagedBackupPair> stageResolvedPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      SqliteDistinctStagedSecret.Generator stagedSecretGenerator,
      @Nullable SqlitePreparedPairPublication preparedPublication) {
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Objects.requireNonNull(stagedSecretGenerator, "stagedSecretGenerator");
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      SqliteProtectedBookStagingTargetPreparation.ensureArtifactParents(
          normalizedBackupFilePath, normalizedBackupBookKeyFilePath);
      SqliteOwnedStagedArtifact stagedBackupFile =
          preparedPublication == null
              ? SqliteOwnedStagedArtifact.create(normalizedBackupFilePath, ".backup-", ".sqlite")
              : preparedPublication.createBookStage(".backup-", ".sqlite");
      @Nullable SqliteOwnedStagedArtifact stagedBackupBookKeyFile = null;
      SqliteStagedBackupPair stagedBackupPair;
      SqliteProtectedBookStagingCheckpoint activeCheckpoint =
          SqliteProtectedBookStagingCheckpoint.BACKUP_EXPORT;
      try {
        checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.BACKUP_EXPORT);
        SqliteProtectedBookStagingFiles.exportBackupUsingSqlite(
            normalizedBookPath, stagedBackupFile.stagedPath(), sourceLease.passphrase().copy());
        activeCheckpoint = SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION;
        SqliteDistinctStagedSecret.GeneratedSecret generatedSecret =
            SqliteDistinctStagedSecret.generate(
                () ->
                    preparedPublication == null
                        ? SqliteOwnedStagedArtifact.create(
                            normalizedBackupBookKeyFilePath, ".backup-key-", ".tmp")
                        : preparedPublication.createSecretStage(".backup-key-", ".tmp"),
                sourceLease.passphrase(),
                SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                checkpointListener,
                stagedSecretGenerator);
        stagedBackupBookKeyFile = generatedSecret.stagedSecretFile();
        try (SqliteBookPassphrase stagedBackupPassphrase = generatedSecret.passphrase()) {
          activeCheckpoint = SqliteProtectedBookStagingCheckpoint.BACKUP_REKEY;
          checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.BACKUP_REKEY);
          SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
              stagedBackupFile.stagedPath(),
              sourceLease.passphrase().copy(),
              stagedBackupPassphrase.copy());
          SqlitePreparedPairPublication.@Nullable PublicationReservations reservations =
              preparedPublication == null ? null : preparedPublication.transferReservations();
          stagedBackupPair =
              createStagedBackupPair(
                  new SqliteStagedProtectedBookPairArtifacts(
                      stagedBackupFile,
                      normalizedBackupFilePath,
                      Objects.requireNonNull(stagedBackupBookKeyFile, "stagedBackupBookKeyFile"),
                      normalizedBackupBookKeyFilePath),
                  stagedBackupPassphrase.utf8BytesCopy(),
                  verificationSupport,
                  reservations);
        }
        return MaintenanceDecision.accepted(stagedBackupPair);
      } catch (ContractFailureException exception) {
        SqliteProtectedBookStagingFailure.releaseAllRetainedPreservingContractFailure(
            exception, stagedBackupFile, stagedBackupBookKeyFile);
        throw exception;
      } catch (SqliteProtectedBookStagingFiles.BackupExportFailure failure) {
        SqliteOwnedStagedArtifact.releaseAllRetained(stagedBackupFile, stagedBackupBookKeyFile);
        return SqliteProtectedBookStagingFailure.at(
            normalizedBackupFilePath, "backupFilePath", failure.publicFailureMessage());
      } catch (RuntimeException exception) {
        SqliteOwnedStagedArtifact.releaseAllRetained(stagedBackupFile, stagedBackupBookKeyFile);
        return SqliteProtectedBookStagingFailure.at(
            normalizedBackupFilePath, "backupFilePath", activeCheckpoint);
      }
    } finally {
      sourceLease.wipe();
    }
  }

  private static SqliteStagedBackupPair createStagedBackupPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqlitePreparedPairPublication.@Nullable PublicationReservations reservations) {
    if (reservations == null) {
      return SqliteStagedBackupPairFactory.create(
          artifacts,
          backupPassphraseBytes,
          verificationSupport,
          Files::createLink,
          Files::createLink,
          null,
          null,
          SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer());
    }
    return SqliteStagedBackupPairFactory.create(
        artifacts,
        backupPassphraseBytes,
        verificationSupport,
        new SqliteStagedBackupPair.PublicationDependencies(
            Files::createLink,
            Files::createLink,
            reservations.bookReservation(),
            reservations.secretReservation(),
            SqliteProtectedBookPublicationSupport.productionPairDirectoryForcer(),
            SqliteSecureRegularFileAccess::forceFile,
            reservations.capabilityWitnesses()));
  }
}
