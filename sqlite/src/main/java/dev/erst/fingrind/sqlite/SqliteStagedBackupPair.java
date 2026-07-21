package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staged encrypted backup pair implementation. */
final class SqliteStagedBackupPair implements StagedBackupPair {
  private final SqliteOwnedStagedArtifact stagedBackupFile;
  private final Path finalBackupFilePath;
  private final SqliteOwnedStagedArtifact stagedBackupBookKeyFile;
  private final Path finalBackupBookKeyFilePath;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final SqliteBackupPairPublication publication;
  private @Nullable SqliteBookPassphrase backupPassphrase;
  private boolean backupFilePublished;
  private boolean backupKeyFilePublished;
  private boolean artifactSealed;
  private boolean finished;

  SqliteStagedBackupPair(
      SqliteStagedProtectedBookPairArtifacts artifacts,
      byte[] backupPassphraseBytes,
      SqliteProtectedBookVerificationSupport verificationSupport,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupKeyLinkCreator,
      SqliteProtectedBookPublicationSupport.NoReplaceLinkCreator backupFileLinkCreator,
      @Nullable SqliteOwnedDestinationReservation backupFileReservation,
      @Nullable SqliteOwnedDestinationReservation backupKeyReservation) {
    SqliteStagedProtectedBookPairArtifacts checkedArtifacts =
        Objects.requireNonNull(artifacts, "artifacts");
    this.stagedBackupFile = checkedArtifacts.stagedBookFile();
    this.finalBackupFilePath = checkedArtifacts.bookTargetPath();
    this.stagedBackupBookKeyFile = checkedArtifacts.stagedSecretFile();
    this.finalBackupBookKeyFilePath = checkedArtifacts.secretTargetPath();
    this.backupPassphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            "staged protected-book backup passphrase",
            Objects.requireNonNull(backupPassphraseBytes, "backupPassphraseBytes"));
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
    this.publication =
        new SqliteBackupPairPublication(
            backupKeyLinkCreator,
            backupFileLinkCreator,
            backupFileReservation,
            backupKeyReservation);
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedBackup() {
    requireUnsealedSnapshot();
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(
            stagedBackupFile.stagedPath(), currentBackupPassphrase().copy()));
  }

  @Override
  public byte[] snapshot() {
    requireUnsealedSnapshot();
    stagedBackupFile.requireIntactFor(finalBackupFilePath);
    try {
      return Files.readAllBytes(stagedBackupFile.stagedPath());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the staged encrypted backup snapshot.", exception);
    }
  }

  @Override
  public void sealArtifact(byte[] artifact) {
    requireUnsealedSnapshot();
    byte[] checkedArtifact = Objects.requireNonNull(artifact, "artifact").clone();
    byte[] snapshot = snapshot();
    if (checkedArtifact.length <= snapshot.length
        || !Arrays.equals(snapshot, Arrays.copyOf(checkedArtifact, snapshot.length))) {
      throw new IllegalArgumentException(
          "Backup artifact must begin with the exact staged encrypted snapshot.");
    }
    try (FileChannel channel =
        FileChannel.open(
            stagedBackupFile.stagedPath(),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer buffer = ByteBuffer.wrap(checkedArtifact);
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
      channel.force(true);
      artifactSealed = true;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to seal the staged attested backup artifact.", exception);
    }
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    try (SqliteBookPassphrase ignored = takeBackupPassphrase()) {
      stagedBackupFile.requireIntactFor(finalBackupFilePath);
      stagedBackupBookKeyFile.requireIntactFor(finalBackupBookKeyFilePath);
      publishBackupKey();
      backupKeyFilePublished = true;
      publishBackupFile();
      backupFilePublished = true;
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      finishAfterFailedPublication();
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.SecretTargetOccupied(exception.targetPath()),
          exception);
    } catch (SqliteCallerPathContractException exception) {
      finishAfterFailedPublication();
      throw new ProtectedBookMaintenanceRejectionException(
          SqliteCallerPathFailureMapper.maintenanceRejection(
              dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                  .BACKUP_KEY_TARGET,
              exception),
          exception);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      finishAfterFailedPublication();
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(finalBackupFilePath),
          exception);
    } catch (IOException exception) {
      finishAfterFailedPublication();
      throw new IllegalStateException(
          "Failed to publish the staged FinGrind backup pair.", exception);
    } catch (RuntimeException exception) {
      finishAfterFailedPublication();
      throw exception;
    }
    finishAfterSuccessfulPublication();
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    try {
      rollbackPublishedBackupArtifacts();
    } finally {
      closeUnusedBackupPassphrase();
      finished = true;
    }
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }

  private SqliteBookPassphrase currentBackupPassphrase() {
    return Objects.requireNonNull(backupPassphrase, "backupPassphrase");
  }

  private void requireUnsealedSnapshot() {
    if (artifactSealed) {
      throw new IllegalStateException(
          "The staged backup snapshot was already sealed into its attestation artifact.");
    }
  }

  private SqliteBookPassphrase takeBackupPassphrase() {
    SqliteBookPassphrase passphrase = currentBackupPassphrase();
    backupPassphrase = null;
    return passphrase;
  }

  private void closeUnusedBackupPassphrase() {
    if (backupPassphrase != null) {
      backupPassphrase.close();
      backupPassphrase = null;
    }
  }

  private void rollbackPublishedBackupArtifacts() {
    if (backupKeyFilePublished) {
      SqliteProtectedBookPublicationRecovery.removePublishedSecretIfOwned(
          finalBackupBookKeyFilePath,
          stagedBackupBookKeyFile,
          "rolling back one interrupted generated backup key publication");
    }
    try {
      if (!backupFilePublished) {
        stagedBackupFile.discard();
      }
    } finally {
      try {
        stagedBackupBookKeyFile.discard();
      } finally {
        closeReservations();
      }
    }
  }

  private void finishAfterSuccessfulPublication() {
    try {
      discardCommittedStages();
    } catch (RuntimeException cleanupFailure) {
      // The externally visible pair is already durable, so cleanup cannot recast success as
      // failure.
      SqliteBestEffort.reportCleanupFailure(
          "discarding owned stages after protected-book backup publication", cleanupFailure);
    } finally {
      finished = true;
    }
  }

  private void discardCommittedStages() {
    try {
      stagedBackupFile.discard();
    } finally {
      try {
        stagedBackupBookKeyFile.discard();
      } finally {
        closeReservations();
      }
    }
  }

  private void finishAfterFailedPublication() {
    try {
      rollbackPublishedBackupArtifacts();
    } catch (RuntimeException cleanupFailure) {
      finished = true;
      throw new IllegalStateException(
          "Failed to roll back the staged FinGrind backup pair; durable owned stages remain for recovery.",
          cleanupFailure);
    }
    finished = true;
  }

  private void publishBackupKey() throws IOException {
    publication.publishKey(
        stagedBackupBookKeyFile, finalBackupBookKeyFilePath, finalBackupBookKeyFilePath);
  }

  private void publishBackupFile() throws IOException {
    publication.publishBook(stagedBackupFile, finalBackupFilePath);
  }

  private void closeReservations() {
    publication.closeReservations();
  }
}
