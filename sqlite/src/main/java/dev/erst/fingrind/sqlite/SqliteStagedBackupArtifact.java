package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Owns the attestation sealing transition for one staged encrypted backup artifact. */
final class SqliteStagedBackupArtifact {
  private final SqliteOwnedStagedArtifact stagedFile;
  private final Path finalFilePath;
  private final ArtifactReader artifactReader;
  private final ArtifactWriter artifactWriter;
  private boolean sealed;

  SqliteStagedBackupArtifact(SqliteOwnedStagedArtifact stagedFile, Path finalFilePath) {
    this(
        stagedFile,
        finalFilePath,
        SqliteOwnedRegularFileAccess::readOwnedAllBytes,
        SqliteStagedBackupArtifact::writeArtifact);
  }

  /** Same-package I/O seam for proving that snapshot and sealing failures preserve the stage. */
  SqliteStagedBackupArtifact(
      SqliteOwnedStagedArtifact stagedFile,
      Path finalFilePath,
      ArtifactReader artifactReader,
      ArtifactWriter artifactWriter) {
    this.stagedFile = Objects.requireNonNull(stagedFile, "stagedFile");
    this.finalFilePath = Objects.requireNonNull(finalFilePath, "finalFilePath");
    this.artifactReader = Objects.requireNonNull(artifactReader, "artifactReader");
    this.artifactWriter = Objects.requireNonNull(artifactWriter, "artifactWriter");
  }

  byte[] snapshot() {
    requireUnsealed();
    stagedFile.requireIntactFor(finalFilePath);
    try {
      return artifactReader.read(stagedFile.stagedPath());
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
      artifactWriter.write(stagedFile.stagedPath(), checkedArtifact);
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

  private static void writeArtifact(Path stagedPath, byte[] artifact) throws IOException {
    try (PrivateOutputFile.OpenedFile channel =
        SqliteOwnedRegularFileAccess.openTruncatingWrite(stagedPath)) {
      ByteBuffer buffer = ByteBuffer.wrap(artifact);
      while (buffer.hasRemaining()) {
        if (channel.write(buffer) <= 0) {
          throw new IOException("Failed to write the complete staged attested backup artifact.");
        }
      }
      channel.force();
    }
  }

  /** Reads one staged backup artifact through the exact owner-only access boundary. */
  @FunctionalInterface
  interface ArtifactReader {
    /** Reads the staged artifact at the supplied path. */
    byte[] read(Path stagedPath) throws IOException;
  }

  /** Writes one staged backup artifact through the exact owner-only access boundary. */
  @FunctionalInterface
  interface ArtifactWriter {
    /** Writes the supplied artifact to the staged path. */
    void write(Path stagedPath, byte[] artifact) throws IOException;
  }
}
