package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** File and sidecar hardening for protected SQLite book artifacts. */
final class SqliteBookArtifactSecurity {
  private static final List<String> SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");
  private static final Set<PosixFilePermission> POSIX_BOOK_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_BOOK_FILE_PERMISSIONS =
      Set.of(
          AclEntryPermission.READ_DATA,
          AclEntryPermission.WRITE_DATA,
          AclEntryPermission.APPEND_DATA,
          AclEntryPermission.READ_NAMED_ATTRS,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.READ_ATTRIBUTES,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.READ_ACL,
          AclEntryPermission.SYNCHRONIZE);

  private SqliteBookArtifactSecurity() {}

  static void hardenBookArtifacts(Path normalizedBookPath) throws IOException {
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(normalizedBookPath);
    Path parentDirectory =
        SqliteBookFilesystemSupport.requireBookParentDirectory(normalizedBookPath);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      SqliteBookDirectorySecurity.requireSecureExistingDirectory(parentDirectory);
    }
    requireRegularNonSymlinkFileIfExists(normalizedBookPath);
    hardenExistingFile(normalizedBookPath);
    String baseFileName =
        Objects.requireNonNull(normalizedBookPath.getFileName(), "normalizedBookPath fileName")
            .toString();
    for (String suffix : SIDECAR_SUFFIXES) {
      hardenExistingFile(normalizedBookPath.resolveSibling(baseFileName + suffix));
    }
  }

  static void hardenOwnerOnlyFile(Path normalizedPath) throws IOException {
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(normalizedPath);
    Path parentDirectory = SqliteBookFilesystemSupport.requireBookParentDirectory(normalizedPath);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      SqliteBookDirectorySecurity.requireSecureExistingDirectory(parentDirectory);
    }
    requireRegularNonSymlinkFileIfExists(normalizedPath);
    hardenExistingFile(normalizedPath);
  }

  static void requireRegularNonSymlinkFileIfExists(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    if (!Files.exists(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (!Files.isRegularFile(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind SQLite book path must resolve to one regular non-symlink file: "
              + SqliteBookFilesystemSupport.redactedPath(normalizedBookPath));
    }
  }

  private static void hardenExistingFile(Path filePath) throws IOException {
    if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (SqliteBookFilesystemSupport.supportsPosix(filePath)) {
      Files.setPosixFilePermissions(filePath, POSIX_BOOK_FILE_PERMISSIONS);
      return;
    }
    SqliteBookAclSupport.applyOwnerOnlyAcl(filePath, WINDOWS_OWNER_BOOK_FILE_PERMISSIONS);
  }
}
