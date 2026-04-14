package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads one UTF-8 book passphrase file. */
public final class SqliteBookKeyFile {
  private SqliteBookKeyFile() {}

  /** Reads and normalizes one UTF-8 passphrase file. */
  public static SqliteBookPassphrase load(Path bookKeyFilePath) {
    Path normalizedPath = normalize(bookKeyFilePath);
    return SqliteBookPassphrase.fromUtf8Bytes(normalizedPath.toString(), readBytes(normalizedPath));
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static byte[] readBytes(Path bookKeyFilePath) {
    try {
      return Files.readAllBytes(bookKeyFilePath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the FinGrind book key file: " + bookKeyFilePath, exception);
    }
  }
}
