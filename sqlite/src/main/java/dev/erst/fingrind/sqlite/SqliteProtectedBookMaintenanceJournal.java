package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Appends durable adjacent maintenance audit records beside one protected-book path. */
final class SqliteProtectedBookMaintenanceJournal {
  private static final String JOURNAL_SUFFIX = ".maintenance-log.jsonl";

  private SqliteProtectedBookMaintenanceJournal() {}

  static void append(ProtectedBookMaintenanceEvent maintenanceEvent) {
    Objects.requireNonNull(maintenanceEvent, "maintenanceEvent");
    Path journalPath = journalPath(maintenanceEvent.bookFilePath());
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(journalPath);
      ensureRegularNonSymlinkJournalFile(journalPath);
      String encodedEvent = encode(maintenanceEvent) + System.lineSeparator();
      try (FileChannel channel =
          FileChannel.open(
              journalPath,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              StandardOpenOption.APPEND)) {
        channel.write(ByteBuffer.wrap(encodedEvent.getBytes(StandardCharsets.UTF_8)));
        channel.force(true);
      }
      SqliteBookFileSecurity.hardenOwnerOnlyFile(journalPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to append the protected-book maintenance journal at " + journalPath + ".",
          exception);
    }
  }

  static Path journalPath(Path normalizedBookPath) {
    Path normalized =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath")
            .toAbsolutePath()
            .normalize();
    String bookFileName =
        Objects.requireNonNull(normalized.getFileName(), "normalizedBookPath fileName").toString();
    return normalized.resolveSibling(bookFileName + JOURNAL_SUFFIX);
  }

  private static void ensureRegularNonSymlinkJournalFile(Path journalPath) throws IOException {
    if (!Files.exists(journalPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (!Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The protected-book maintenance journal path must remain one regular non-symlink file: "
              + journalPath);
    }
  }

  private static String encode(ProtectedBookMaintenanceEvent maintenanceEvent) {
    StringBuilder builder = new StringBuilder(256);
    builder.append('{');
    appendField(builder, "recordedAt", maintenanceEvent.recordedAt().toString());
    appendField(builder, "eventKind", maintenanceEvent.kind().wireValue());
    appendField(builder, "bookFile", hint(maintenanceEvent.bookFilePath()).value());
    appendNullableField(builder, "backupFile", maintenanceEvent.backupFilePath());
    appendNullableField(builder, "backupBookKeyFile", maintenanceEvent.backupBookKeyFilePath());
    appendPathList(builder, "rollbackArtifacts", maintenanceEvent.rollbackArtifactPaths());
    appendNullableField(builder, "rollbackArtifact", maintenanceEvent.rollbackArtifactPath());
    builder.append('}');
    return builder.toString();
  }

  private static void appendNullableField(
      StringBuilder builder, String fieldName, @Nullable Path nullablePath) {
    if (nullablePath == null) {
      return;
    }
    appendField(builder, fieldName, hint(nullablePath).value());
  }

  private static void appendPathList(StringBuilder builder, String fieldName, List<Path> paths) {
    appendCommaIfNeeded(builder);
    builder.append('"').append(escape(fieldName)).append("\":[");
    for (int index = 0; index < paths.size(); index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append('"').append(escape(hint(paths.get(index)).value())).append('"');
    }
    builder.append(']');
  }

  private static void appendField(StringBuilder builder, String fieldName, String value) {
    appendCommaIfNeeded(builder);
    builder.append('"').append(escape(fieldName)).append("\":\"").append(escape(value)).append('"');
  }

  private static void appendCommaIfNeeded(StringBuilder builder) {
    if (builder.length() > 1) {
      builder.append(',');
    }
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
