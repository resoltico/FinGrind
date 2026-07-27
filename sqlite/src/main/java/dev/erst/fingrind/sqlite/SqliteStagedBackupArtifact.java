package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Owns the attestation sealing transition for one staged encrypted backup artifact. */
final class SqliteStagedBackupArtifact {
  private final SqliteOwnedStagedArtifact stagedFile;
  private final Path finalFilePath;
  private boolean sealed;

  SqliteStagedBackupArtifact(SqliteOwnedStagedArtifact stagedFile, Path finalFilePath) {
    this.stagedFile = Objects.requireNonNull(stagedFile, "stagedFile");
    this.finalFilePath = Objects.requireNonNull(finalFilePath, "finalFilePath");
  }

  byte[] snapshot() {
    requireUnsealed();
    stagedFile.requireIntactFor(finalFilePath);
    try {
      return SqliteSecureRegularFileAccess.readAllBytes(stagedFile.stagedPath());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the staged encrypted backup snapshot.", exception);
    }
  }

  void seal(byte[] artifact) {
    requireUnsealed();
    byte[] checkedArtifact = Objects.requireNonNull(artifact, "artifact").clone();
    byte[] snapshot = snapshot();
    if (checkedArtifact.length <= snapshot.length
        || !Arrays.equals(snapshot, Arrays.copyOf(checkedArtifact, snapshot.length))) {
      throw new IllegalArgumentException(
          "Backup artifact must begin with the exact staged encrypted snapshot.");
    }
    try {
      write(checkedArtifact);
      sealed = true;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to seal the staged attested backup artifact.", exception);
    }
  }

  void requireUnsealed() {
    if (sealed) {
      throw new IllegalStateException(
          "The staged backup snapshot was already sealed into its attestation artifact.");
    }
  }

  void requireSealed() {
    if (!sealed) {
      throw new IllegalStateException(
          "A staged protected-book backup pair must be sealed into its attestation artifact before publication.");
    }
  }

  private void write(byte[] artifact) throws IOException {
    try (FileChannel channel =
        SqliteSecureRegularFileAccess.openTruncatingWrite(stagedFile.stagedPath())) {
      ByteBuffer buffer = ByteBuffer.wrap(artifact);
      while (buffer.hasRemaining()) {
        if (channel.write(buffer) <= 0) {
          throw new IOException("Failed to write the complete staged attested backup artifact.");
        }
      }
      channel.force(true);
    }
  }
}
