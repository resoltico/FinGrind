package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Decodes and validates one untrusted current owned-stage record. */
final class SqliteOwnedStageRecordDecoder {
  private SqliteOwnedStageRecordDecoder() {}

  static Optional<SqliteOwnedStageRecordCodec.CurrentOwnerRecord> decode(Path recordPath) {
    if (!SqliteOwnedStageRecordCodec.isCurrentRecordFile(recordPath)
        || !java.nio.file.Files.isRegularFile(
            recordPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      Optional<SqliteOwnedStageRecordCodec.CurrentOwnerRecord> decoded = decodedRecord(recordPath);
      if (decoded.isEmpty() || !remainsBesideRecord(recordPath, decoded.orElseThrow())) {
        return Optional.empty();
      }
      return decoded;
    } catch (SqliteCallerPathContractException identityFailure) {
      throw identityFailure;
    } catch (IOException | IllegalArgumentException | NullPointerException exception) {
      return Optional.empty();
    }
  }

  private static Optional<SqliteOwnedStageRecordCodec.CurrentOwnerRecord> decodedRecord(
      Path recordPath) throws IOException {
    List<String> lines =
        SqliteSecureRegularFileAccess.readUtf8LinesBounded(
            recordPath, SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES, 4);
    if (lines.size() != 3 || !SqliteOwnedStageRecordCodec.RECORD_MAGIC.equals(lines.getFirst())) {
      return Optional.empty();
    }
    Optional<Path> finalPath = decode(recordPath.getFileSystem(), lines.get(1), "target=");
    Optional<Path> stagedPath = decode(recordPath.getFileSystem(), lines.get(2), "stage=");
    if (finalPath.isEmpty() || stagedPath.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new SqliteOwnedStageRecordCodec.CurrentOwnerRecord(
            finalPath.orElseThrow(), stagedPath.orElseThrow()));
  }

  private static boolean remainsBesideRecord(
      Path recordPath, SqliteOwnedStageRecordCodec.CurrentOwnerRecord record) throws IOException {
    Path parent = recordPath.getParent();
    if (parent == null
        || SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            record.finalPath(), record.stagedPath())) {
      return false;
    }
    return SqliteProtectedBookPathIdentity.sameExistingFilesystemObject(
            parent,
            Objects.requireNonNull(record.finalPath().getParent(), "finalPath parent"),
            record.finalPath())
        && SqliteProtectedBookPathIdentity.sameExistingFilesystemObject(
            parent,
            Objects.requireNonNull(record.stagedPath().getParent(), "stagedPath parent"),
            record.finalPath());
  }

  private static Optional<Path> decode(FileSystem fileSystem, String line, String prefix) {
    Objects.requireNonNull(fileSystem, "fileSystem");
    if (!line.startsWith(prefix)) {
      return Optional.empty();
    }
    String encodedPath = line.substring(prefix.length());
    if (encodedPath.isEmpty()
        || encodedPath.length() % 4 == 1
        || !encodedPath.matches("[A-Za-z0-9_-]+")) {
      return Optional.empty();
    }
    String decodedPath =
        new String(
            Base64.getUrlDecoder().decode(encodedPath), java.nio.charset.StandardCharsets.UTF_8);
    if (decodedPath.indexOf('\u0000') >= 0) {
      return Optional.empty();
    }
    return Optional.of(fileSystem.getPath(decodedPath).toAbsolutePath().normalize());
  }
}
