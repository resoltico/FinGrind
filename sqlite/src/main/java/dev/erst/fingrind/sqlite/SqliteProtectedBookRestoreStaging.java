package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;

/** Creates a rekeyed restored-book pair in authenticated journal-owned stages. */
final class SqliteProtectedBookRestoreStaging {
  private SqliteProtectedBookRestoreStaging() {}

  static MaintenanceDecision<StagedRestoredBookPair> stageResolvedPair(
      Path normalizedSourceBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    Objects.requireNonNull(preparedPublication, "preparedPublication");
    return stageJournaledPair(
        normalizedSourceBookPath,
        preparedPublication,
        sourcePassphrase,
        verificationSupport,
        SqliteProtectedBookStagingCheckpointListener.none());
  }

  /**
   * Materializes one restored pair only in stages the authenticated transaction already reserved.
   */
  private static MaintenanceDecision<StagedRestoredBookPair> stageJournaledPair(
      Path normalizedSourceBookPath,
      SqlitePreparedPairPublication preparedPublication,
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookStagingCheckpointListener checkpointListener) {
    SqlitePublicationTransactionPair publication = preparedPublication.journaledPair();
    SqliteStagingSourcePassphraseLease sourceLease =
        SqliteStagingSourcePassphraseLease.take(sourcePassphrase);
    try {
      Path bookStagePath = publication.bookStagePath();
      Path secretStagePath = publication.secretStagePath();
      checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.RESTORE_COPY);
      SqliteOwnedRegularFileAccess.createNewEmptyFile(bookStagePath);
      copySourceIntoExistingOwnedStage(normalizedSourceBookPath, bookStagePath);
      try (SqliteBookPassphrase generatedPassphrase =
          SqliteDistinctGeneratedSecret.generate(
              sourceLease.passphrase(),
              SqliteProtectedBookStagingCheckpoint.RESTORE_SECRET_GENERATION,
              checkpointListener)) {
        SqliteOwnedRegularFileAccess.createNewEmptyFile(secretStagePath);
        byte[] generatedBytes = generatedPassphrase.utf8BytesCopy();
        try {
          SqliteBookKeyFileMaterial.writeEncodedPassphraseIntoExistingOwnedStage(
              secretStagePath, generatedBytes);
        } finally {
          java.util.Arrays.fill(generatedBytes, (byte) 0);
        }
        checkpointListener.reached(SqliteProtectedBookStagingCheckpoint.RESTORE_REKEY);
        SqliteProtectedBookStagingFiles.rekeyStagedBookCopy(
            bookStagePath, sourceLease.passphrase().copy(), generatedPassphrase.copy());
        return MaintenanceDecision.accepted(
            new SqliteJournaledStagedRestoredBookPair(
                publication, bookStagePath, generatedPassphrase.copy(), verificationSupport));
      }
    } catch (IOException | RuntimeException failure) {
      throw publication.incompleteFailure(
          preparedPublication.bookTargetPath(), "bookFilePath", failure);
    } finally {
      sourceLease.wipe();
    }
  }

  /** Copies one nofollow source into an exact already-owned stage and force-confirms its bytes. */
  static void copySourceIntoExistingOwnedStage(Path sourcePath, Path stagedPath)
      throws IOException {
    try (InputStream source = SqliteSecureRegularFileAccess.openRead(sourcePath);
        PrivateOutputFile.OpenedFile destination =
            SqliteOwnedRegularFileAccess.openTruncatingWrite(stagedPath)) {
      byte[] buffer = new byte[16 * 1024];
      int read = source.read(buffer);
      while (read >= 0) {
        if (read == 0) {
          throw new IOException("Failed to read the complete protected-book restore source.");
        }
        ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
        while (bytes.hasRemaining()) {
          if (destination.write(bytes) <= 0) {
            throw new IOException("Failed to write the complete protected-book restore stage.");
          }
        }
        read = source.read(buffer);
      }
      destination.force();
    }
  }
}
