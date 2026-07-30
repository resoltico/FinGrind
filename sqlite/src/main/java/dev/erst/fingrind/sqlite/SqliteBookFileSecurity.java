package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;

/** Facade over owner-only protection for encrypted SQLite book files and directories. */
final class SqliteBookFileSecurity {
  private SqliteBookFileSecurity() {}

  static void ensureSecureParentDirectory(Path normalizedBookPath) throws IOException {
    SqliteBookDirectorySecurity.ensureSecureParentDirectory(normalizedBookPath);
  }

  static void requireExistingSecureParentDirectory(Path normalizedBookPath) throws IOException {
    SqliteBookDirectorySecurity.requireExistingSecureParentDirectory(normalizedBookPath);
  }

  /** Validates one existing protected book without repairing permissions or ACLs. */
  static void requireSecureExistingBookFile(Path normalizedBookPath, boolean requiresWrite)
      throws IOException {
    SqliteBookArtifactSecurity.requireSecureExistingBookFile(normalizedBookPath, requiresWrite);
  }

  /** Atomically creates one empty protected book through the owner-only core capability. */
  static void createNewOwnerOnlyBookFile(Path normalizedBookPath) throws IOException {
    SqliteBookArtifactSecurity.createNewOwnerOnlyBookFile(normalizedBookPath);
  }

  static void requireSupportedSecureFilesystem(Path path) {
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(path);
  }
}
