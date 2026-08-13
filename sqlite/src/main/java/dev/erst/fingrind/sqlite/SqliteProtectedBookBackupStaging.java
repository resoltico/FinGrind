package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.nio.file.Path;
import java.util.Objects;

/** Creates an independently encrypted backup pair in authenticated journal-owned stages. */
final class SqliteProtectedBookBackupStaging {
  private SqliteProtectedBookBackupStaging() {}

  static MaintenanceDecision<StagedBackupPair> stageResolvedPair(
      Path normalizedBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageJournaledPair(
        normalizedBookPath,
        preparedPublication,
        sourcePassphrase,
        verificationSupport,
        SqliteProtectedBookStagingCheckpointListener.none());
  }

  /** Materializes one backup pair only in stages the authenticated transaction already reserved. */
  private static MaintenanceDecision<StagedBackupPair> stageJournaledPair(
      Path normalizedBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener) {
    SqlitePublicationTransactionPair publication = preparedPublication.journaledPair();
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      Path backupStagePath = publication.bookStagePath();
      Path secretStagePath = publication.secretStagePath();
      SqliteOwnedRegularFileAccess.createNewEmptyFile(backupStagePath);
      checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.BACKUP_EXPORT);
      SqliteProtectedBookStagingFiles.exportBackupUsingSqlite(
          normalizedBookPath, backupStagePath, sourceLease.passphrase().copy());
      try (SqliteBookPassphrase generatedPassphrase =
          SqliteDistinctGeneratedSecret.generate(
              sourceLease.passphrase(),
              SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
              checkpointListener)) {
        SqliteOwnedRegularFileAccess.createNewEmptyFile(secretStagePath);
        byte[] generatedBytes = generatedPassphrase.utf8BytesCopy();
        try {
          SqliteBookKeyFileMaterial.writeEncodedPassphraseIntoExistingOwnedStage(
              secretStagePath, generatedBytes);
        } finally {
          java.util.Arrays.fill(generatedBytes, (byte) 0);
        }
        checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.BACKUP_REKEY);
        SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
            backupStagePath, sourceLease.passphrase().copy(), generatedPassphrase.copy());
        return MaintenanceDecision.accepted(
            new SqliteJournaledStagedBackupPair(
                publication, backupStagePath, generatedPassphrase.copy(), verificationSupport));
      }
    } catch (java.io.IOException | RuntimeException failure) {
      throw publication.incompleteFailure(
          preparedPublication.bookTargetPath(), "backupFilePath", failure);
    } finally {
      sourceLease.wipe();
    }
  }
}
