package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.IOException;
import java.nio.file.Path;

/** Facade over owner-only protection for protected FinGrind book-key files. */
final class SqliteBookKeyFileSecurity {
  static final String POSIX_OWNER_READ_WRITE_DESCRIPTOR =
      SqliteBookKeyFileSecuritySupport.POSIX_OWNER_READ_WRITE_DESCRIPTOR;
  static final String WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR =
      SqliteBookKeyFileSecuritySupport.WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR;

  private SqliteBookKeyFileSecurity() {}

  static String generatedPermissionsDescriptor(Path normalizedPath) {
    return SqliteBookKeyFileSecuritySupport.generatedPermissionsDescriptor(normalizedPath);
  }

  static void requireSupportedSecureFilesystem(Path normalizedPath) {
    SqliteBookKeyFileSecuritySupport.requireSupportedSecureFilesystem(normalizedPath);
  }

  static void ensureSecureParentDirectory(Path normalizedPath) throws IOException {
    SqliteBookKeyFileDirectorySecurity.ensureSecureParentDirectory(normalizedPath);
  }

  static void requireExistingSecureParentDirectory(Path normalizedPath) throws IOException {
    SqliteBookKeyFileDirectorySecurity.requireExistingSecureParentDirectory(normalizedPath);
  }

  static void createSecureEmptyFile(Path normalizedPath) throws IOException {
    SqliteBookKeyFileArtifactSecurity.createSecureEmptyFile(normalizedPath);
  }

  static ContractDecision<Path> requireSecureKeyFile(Path bookKeyFilePath) {
    return SqliteBookKeyFileArtifactSecurity.requireSecureKeyFile(bookKeyFilePath);
  }

  static ContractDecision<Path> requireSecureKeyFile(
      Path bookKeyFilePath, SqliteKeyFileSecurityInspector securityInspector) {
    return SqliteBookKeyFileArtifactSecurity.requireSecureKeyFile(
        bookKeyFilePath, securityInspector);
  }

  static void hardenDirectory(Path directoryPath) throws IOException {
    SqliteBookKeyFileDirectorySecurity.hardenDirectory(directoryPath);
  }
}
