package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Binary-safe codec for durable owned-stage records. */
final class SqliteOwnedStageRecordCodec {
  /**
   * Opaque owner-record namespace.
   *
   * <p>The final artifact name is deliberately not part of this filename. A recovery caller can
   * arrive through a different spelling of the same final leaf, while the durable record itself
   * binds the canonical target path in its contents. Discovery therefore scans this namespace and
   * validates the encoded target instead of deriving authority from a caller-supplied basename.
   */
  static final String RECORD_PREFIX = ".fingrind-maintenance-stage-";

  static final String RECORD_SUFFIX = ".owner";
  private static final String STAGE_FILE_PREFIX = ".fingrind-stage";
  static final String RECORD_MAGIC = "fingrind-maintenance-stage-v2";
  static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private SqliteOwnedStageRecordCodec() {}

  static Path stagedPath(Path finalPath, String infix, String suffix) {
    Path parent = Objects.requireNonNull(finalPath.getParent(), "finalPath parent");
    // SQLite may create a rollback-journal sidecar while materializing this file. Keeping the
    // stage name independent of the user-selected artifact name preserves Windows path headroom.
    return parent.resolve(
        STAGE_FILE_PREFIX
            + Objects.requireNonNull(infix, "infix")
            + UUID.randomUUID()
            + Objects.requireNonNull(suffix, "suffix"));
  }

  static SqliteOwnedStageRecord write(Path finalPath, Path stagedPath) {
    return write(finalPath, stagedPath, UUID::randomUUID);
  }

  static SqliteOwnedStageRecord write(
      Path finalPath, Path stagedPath, Supplier<UUID> tokenSupplier) {
    Objects.requireNonNull(tokenSupplier, "tokenSupplier");
    for (int attempt = 0; attempt < 8; attempt++) {
      Path recordPath = recordPath(finalPath, tokenSupplier.get());
      boolean recordWritten;
      try {
        write(recordPath, finalPath, stagedPath);
        recordWritten = true;
      } catch (java.nio.file.FileAlreadyExistsException exception) {
        recordWritten = false;
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to record one owned maintenance stage beside "
                + SqliteMachinePaths.absoluteValue(finalPath)
                + ".",
            exception);
      }
      if (recordWritten) {
        return new SqliteOwnedStageRecord(stagedPath, recordPath);
      }
    }
    throw new IllegalStateException(
        "Unable to record an owned maintenance stage beside "
            + SqliteMachinePaths.absoluteValue(finalPath)
            + ".");
  }

  static Path recordPath(Path finalPath, UUID token) {
    Path parent = Objects.requireNonNull(finalPath.getParent(), "finalPath parent");
    return parent.resolve(recordFileName(Objects.requireNonNull(token, "token").toString()));
  }

  static Optional<SqliteOwnedStageRecord> read(Path recordPath, Path expectedFinalPath) {
    Optional<CurrentOwnerRecord> decoded = readCurrent(recordPath);
    if (decoded.isEmpty()
        || !SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            decoded.orElseThrow().finalPath(), expectedFinalPath)) {
      return Optional.empty();
    }
    return Optional.of(new SqliteOwnedStageRecord(decoded.orElseThrow().stagedPath(), recordPath));
  }

  /**
   * Reads one current owner record without assuming the final target through which it was found.
   *
   * <p>The maintenance lease uses this narrow decoder only to establish whether a private stage is
   * derived from one exact final artifact it already owns. Callers must never treat a decoded
   * record as parent-directory authority.
   */
  static Optional<CurrentOwnerRecord> readCurrent(Path recordPath) {
    return SqliteOwnedStageRecordDecoder.decode(recordPath);
  }

  /**
   * Returns whether a stage-owner sidecar is foreign or malformed residue that must fail closed.
   *
   * <p>Valid current opaque records remain inert until a pair claim binds them to both final
   * members. That preserves idempotent recovery of completed pair residue without treating every
   * standalone reservation as a permanent maintenance lock. Retired target-derived records are
   * never parsed, deleted, or recovered.
   */
  static boolean isUnsafeOwnerRecordResidue(Path candidate) {
    String fileName =
        Objects.requireNonNull(candidate.getFileName(), "candidate fileName").toString();
    return isRetiredTargetDerivedRecordFile(fileName)
        || (fileName.startsWith(RECORD_PREFIX) && readCurrent(candidate).isEmpty());
  }

  private static void write(Path recordPath, Path finalPath, Path stagedPath) throws IOException {
    byte[] content =
        String.join(
                "\n",
                RECORD_MAGIC,
                "target=" + encode(finalPath),
                "stage=" + encode(stagedPath),
                "")
            .getBytes(StandardCharsets.UTF_8);
    if (content.length > SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES) {
      throw new IOException("Owned maintenance-stage metadata exceeds its supported size.");
    }
    try (FileChannel channel = SqliteSecureRegularFileAccess.openNewWrite(recordPath)) {
      ByteBuffer bytes = ByteBuffer.wrap(content);
      while (bytes.hasRemaining()) {
        if (channel.write(bytes) <= 0) {
          throw new IOException(
              "Failed to write the complete FinGrind maintenance-stage ownership record.");
        }
      }
      channel.force(true);
    }
  }

  private static String recordFileName(String token) {
    return RECORD_PREFIX + token + RECORD_SUFFIX;
  }

  private static String encode(Path path) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
  }

  static boolean isCurrentRecordFile(Path recordPath) {
    String fileName =
        Objects.requireNonNull(recordPath.getFileName(), "recordPath fileName").toString();
    if (!fileName.startsWith(RECORD_PREFIX) || !fileName.endsWith(RECORD_SUFFIX)) {
      return false;
    }
    String token =
        fileName.substring(RECORD_PREFIX.length(), fileName.length() - RECORD_SUFFIX.length());
    return token.matches(UUID_PATTERN);
  }

  private static boolean isRetiredTargetDerivedRecordFile(String fileName) {
    return fileName.matches("^\\..+\\.fingrind-maintenance-stage-" + UUID_PATTERN + "\\.owner$");
  }

  /** Exact immutable target-and-stage relation decoded from one current owner record. */
  record CurrentOwnerRecord(Path finalPath, Path stagedPath) {
    CurrentOwnerRecord {
      finalPath = Objects.requireNonNull(finalPath, "finalPath").toAbsolutePath().normalize();
      stagedPath = Objects.requireNonNull(stagedPath, "stagedPath").toAbsolutePath().normalize();
    }
  }
}
