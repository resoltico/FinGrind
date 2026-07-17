package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Binary-safe codec for durable owned-stage records. */
final class SqliteOwnedStageRecordCodec {
  private static final String RECORD_INFIX = ".fingrind-maintenance-stage-";
  private static final String RECORD_SUFFIX = ".owner";
  private static final String STAGE_FILE_PREFIX = ".fingrind-stage";
  private static final String RECORD_MAGIC = "fingrind-maintenance-stage-v1";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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
    String fileName =
        Objects.requireNonNull(finalPath.getFileName(), "finalPath fileName").toString();
    return parent.resolve(
        recordFileName(fileName, Objects.requireNonNull(token, "token").toString()));
  }

  static Optional<SqliteOwnedStageRecord> read(Path recordPath, Path expectedFinalPath) {
    if (!isExpectedRecordFile(recordPath, expectedFinalPath)
        || !Files.isRegularFile(recordPath, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      List<String> lines = Files.readAllLines(recordPath, StandardCharsets.UTF_8);
      if (lines.size() != 3 || !RECORD_MAGIC.equals(lines.getFirst())) {
        return Optional.empty();
      }
      Optional<Path> finalPath = decode(recordPath.getFileSystem(), lines.get(1), "target=");
      Optional<Path> stagedPath = decode(recordPath.getFileSystem(), lines.get(2), "stage=");
      Path parent = Objects.requireNonNull(expectedFinalPath.getParent(), "finalPath parent");
      if (finalPath.isEmpty()
          || stagedPath.isEmpty()
          || !finalPath.orElseThrow().equals(expectedFinalPath)
          || !parent.equals(stagedPath.orElseThrow().getParent())) {
        return Optional.empty();
      }
      return Optional.of(new SqliteOwnedStageRecord(stagedPath.orElseThrow(), recordPath));
    } catch (IOException exception) {
      return Optional.empty();
    }
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
    try (FileChannel channel =
        FileChannel.open(recordPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer bytes = ByteBuffer.wrap(content);
      while (bytes.hasRemaining()) {
        channel.write(bytes);
      }
      channel.force(true);
    }
  }

  private static String recordFileName(String finalFileName, String token) {
    return "." + finalFileName + RECORD_INFIX + token + RECORD_SUFFIX;
  }

  private static String encode(Path path) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static boolean isExpectedRecordFile(Path recordPath, Path expectedFinalPath) {
    String fileName =
        Objects.requireNonNull(recordPath.getFileName(), "recordPath fileName").toString();
    String targetFileName =
        Objects.requireNonNull(expectedFinalPath.getFileName(), "finalPath fileName").toString();
    String prefix = "." + targetFileName + RECORD_INFIX;
    if (!fileName.startsWith(prefix) || !fileName.endsWith(RECORD_SUFFIX)) {
      return false;
    }
    String token = fileName.substring(prefix.length(), fileName.length() - RECORD_SUFFIX.length());
    return token.matches(UUID_PATTERN);
  }

  private static Optional<Path> decode(FileSystem fileSystem, String line, String prefix) {
    Objects.requireNonNull(fileSystem, "fileSystem");
    if (!line.startsWith(prefix)) {
      return Optional.empty();
    }
    String encodedPath = line.substring(prefix.length());
    if (!isBase64Url(encodedPath)) {
      return Optional.empty();
    }
    String decodedPath =
        new String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8);
    if (decodedPath.indexOf('\u0000') >= 0) {
      return Optional.empty();
    }
    return Optional.of(fileSystem.getPath(decodedPath).toAbsolutePath().normalize());
  }

  private static boolean isBase64Url(String value) {
    return !value.isEmpty() && value.length() % 4 != 1 && value.matches("[A-Za-z0-9_-]+");
  }
}
