package dev.erst.fingrind.sqlite;

import java.nio.file.Path;

/** Shared helpers for owner-only temporary directories in secret-surface SQLite tests. */
final class SqliteSecretTestPrivateDirectorySupport {
  private SqliteSecretTestPrivateDirectorySupport() {}

  static void hardenOwnerOnlyDirectory(Path directoryPath) {
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(directoryPath);
  }
}
