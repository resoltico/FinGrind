package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helpers for owner-only temporary directories in secret-surface SQLite tests. */
final class SqliteSecretTestPrivateDirectorySupport {
  private SqliteSecretTestPrivateDirectorySupport() {}

  static void hardenOwnerOnlyDirectory(Path directoryPath) {
    try {
      if (Files.isDirectory(directoryPath)) {
        SqliteBookKeyFileSecurity.hardenDirectory(directoryPath);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to harden one SQLite secret test temporary directory: " + directoryPath,
          exception);
    }
  }
}
