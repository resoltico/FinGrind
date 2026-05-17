package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helpers for allocating owner-only temporary directories in SQLite tests. */
final class SqliteTestPrivateDirectorySupport {
  private SqliteTestPrivateDirectorySupport() {}

  static void hardenOwnerOnlyDirectory(Path directoryPath) {
    try {
      if (Files.isDirectory(directoryPath)) {
        SqliteBookFileSecurity.hardenDirectory(directoryPath);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to harden one SQLite test temporary directory: " + directoryPath, exception);
    }
  }

  static Path createOwnerOnlyTempDirectory(String prefix) {
    try {
      Path directoryPath = Files.createTempDirectory(prefix);
      hardenOwnerOnlyDirectory(directoryPath);
      return directoryPath;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to create one owner-only SQLite test temporary directory for prefix "
              + prefix
              + ".",
          exception);
    }
  }
}
