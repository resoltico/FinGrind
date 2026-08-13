package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.core.PrivateOutputFile;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import dev.erst.fingrind.executor.spi.StagedPairPublicationCommitOutcome;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Staged backup pair whose only publication and recovery authority is its transaction journal. */
final class SqliteJournaledStagedBackupPair implements StagedBackupPair {
  private final SqlitePublicationTransactionPair publication;
  private final Path backupStagePath;
  private final SqliteBookPassphrase backupPassphrase;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private final OwnedStageReader ownedStageReader;
  private @org.jspecify.annotations.Nullable StagedPairPublicationCommitOutcome commitOutcome;
  private boolean sealed;
  private boolean closed;

  SqliteJournaledStagedBackupPair(
      SqlitePublicationTransactionPair publication,
      Path backupStagePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    this(
        publication,
        backupStagePath,
        backupPassphrase,
        verificationSupport,
        SqliteOwnedRegularFileAccess::readOwnedAllBytes);
  }

  SqliteJournaledStagedBackupPair(
      SqlitePublicationTransactionPair publication,
      Path backupStagePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport,
      OwnedStageReader ownedStageReader) {
    this.publication = Objects.requireNonNull(publication, "publication");
    this.backupStagePath = Objects.requireNonNull(backupStagePath, "backupStagePath");
    this.backupPassphrase = Objects.requireNonNull(backupPassphrase, "backupPassphrase");
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
    this.ownedStageReader = Objects.requireNonNull(ownedStageReader, "ownedStageReader");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedBackup() {
    requireUnsealed();
    return MaintenanceDecision.accepted(
        verificationSupport.verifyResolvedBook(backupStagePath, backupPassphrase.copy()));
  }

  @Override
  public byte[] snapshot() {
    requireUnsealed();
    try {
      return ownedStageReader.read(backupStagePath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the journal-owned encrypted backup stage.", exception);
    }
  }

  @Override
  public void sealArtifact(byte[] artifact) {
    requireUnsealed();
    byte[] checkedArtifact = Objects.requireNonNull(artifact, "artifact").clone();
    byte[] snapshot = snapshot();
    try {
      if (checkedArtifact.length <= snapshot.length
          || !Arrays.equals(snapshot, Arrays.copyOf(checkedArtifact, snapshot.length))) {
        throw new IllegalArgumentException(
            "Backup artifact must begin with the exact journal-owned encrypted snapshot.");
      }
      writeExactly(backupStagePath, checkedArtifact);
      sealed = true;
    } finally {
      Arrays.fill(snapshot, (byte) 0);
      Arrays.fill(checkedArtifact, (byte) 0);
    }
  }

  @Override
  public StagedPairPublicationCommitOutcome commit() {
    if (commitOutcome != null) {
      return commitOutcome;
    }
    requireOpen();
    if (!sealed) {
      throw new IllegalStateException(
          "A journal-owned protected-book backup must be sealed before publication.");
    }
    try {
      ProtectedBookPairPublication completed = publication.publish();
      close();
      commitOutcome = new StagedPairPublicationCommitOutcome.Published(completed);
      return commitOutcome;
    } catch (PublicationTransactionExecutionException incomplete) {
      close();
      commitOutcome =
          new StagedPairPublicationCommitOutcome.PublicationTransactionIncomplete(
              publication.bookTargetPath(), incomplete.result());
      return commitOutcome;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to publish the journal-owned backup pair.", exception);
    }
  }

  @Override
  public void retainUnpublishedArtifacts() {
    close();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      try {
        backupPassphrase.close();
      } finally {
        publication.releaseStageAccess();
      }
    }
  }

  private void requireUnsealed() {
    requireOpen();
    if (sealed) {
      throw new IllegalStateException(
          "The journal-owned backup snapshot was already sealed into its attestation artifact.");
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("The journal-owned protected-book backup pair is closed.");
    }
  }

  static void writeExactly(Path stagePath, byte[] bytes) {
    try (PrivateOutputFile.OpenedFile channel =
        SqliteOwnedRegularFileAccess.openTruncatingWrite(stagePath)) {
      writeCompletely(channel, ByteBuffer.wrap(bytes));
      channel.force();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to seal the journal-owned backup artifact.", exception);
    }
  }

  static void writeCompletely(PrivateOutputFile.OpenedFile channel, ByteBuffer buffer)
      throws IOException {
    while (buffer.hasRemaining()) {
      if (channel.write(buffer) <= 0) {
        throw new IOException("Failed to write the complete journal-owned backup artifact.");
      }
    }
  }

  /** Reads the one exact journal-owned backup stage. */
  @FunctionalInterface
  interface OwnedStageReader {
    /** Reads the complete bytes through the exact owner-only stage capability. */
    byte[] read(Path stagePath) throws IOException;
  }
}
