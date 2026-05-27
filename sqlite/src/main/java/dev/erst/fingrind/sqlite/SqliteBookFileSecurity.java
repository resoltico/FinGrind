package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;

/** Facade over owner-only protection for encrypted SQLite book files and directories. */
final class SqliteBookFileSecurity {
  private SqliteBookFileSecurity() {}

  static void ensureSecureParentDirectory(Path normalizedBookPath) throws IOException {
    SqliteBookDirectorySecurity.ensureSecureParentDirectory(normalizedBookPath);
  }

  static void hardenBookArtifacts(Path normalizedBookPath) throws IOException {
    SqliteBookArtifactSecurity.hardenBookArtifacts(normalizedBookPath);
  }

  static void hardenOwnerOnlyFile(Path normalizedPath) throws IOException {
    SqliteBookArtifactSecurity.hardenOwnerOnlyFile(normalizedPath);
  }

  static void requireRegularNonSymlinkFileIfExists(Path normalizedBookPath) {
    SqliteBookArtifactSecurity.requireRegularNonSymlinkFileIfExists(normalizedBookPath);
  }

  static void requireSupportedSecureFilesystem(Path path) {
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(path);
  }

  /** Same-package seam for hardening one verified book directory during tests. */
  static void hardenDirectory(Path directoryPath) throws IOException {
    SqliteBookDirectorySecurity.hardenDirectory(directoryPath);
  }
}
