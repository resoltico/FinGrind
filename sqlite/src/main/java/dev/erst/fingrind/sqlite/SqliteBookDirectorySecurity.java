package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;

/** Directory-specific owner-only protection for protected SQLite books. */
final class SqliteBookDirectorySecurity {
  private static final Set<PosixFilePermission> POSIX_BOOK_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_BOOK_DIRECTORY_PERMISSIONS =
      Set.of(
          AclEntryPermission.LIST_DIRECTORY,
          AclEntryPermission.ADD_FILE,
          AclEntryPermission.ADD_SUBDIRECTORY,
          AclEntryPermission.READ_NAMED_ATTRS,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.EXECUTE,
          AclEntryPermission.DELETE_CHILD,
          AclEntryPermission.READ_ATTRIBUTES,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.READ_ACL,
          AclEntryPermission.WRITE_ACL,
          AclEntryPermission.WRITE_OWNER,
          AclEntryPermission.SYNCHRONIZE);
  private static final Set<AclEntryPermission> ACL_DIRECTORY_REQUIRED_PERMISSIONS =
      Set.of(
          AclEntryPermission.LIST_DIRECTORY,
          AclEntryPermission.ADD_FILE,
          AclEntryPermission.EXECUTE);
  private static final Set<AclEntryPermission> ACL_DIRECTORY_ACCESS_PERMISSIONS =
      Set.of(
          AclEntryPermission.LIST_DIRECTORY,
          AclEntryPermission.ADD_FILE,
          AclEntryPermission.ADD_SUBDIRECTORY,
          AclEntryPermission.EXECUTE,
          AclEntryPermission.DELETE_CHILD,
          AclEntryPermission.READ_NAMED_ATTRS,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.READ_ATTRIBUTES,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.READ_ACL,
          AclEntryPermission.WRITE_ACL,
          AclEntryPermission.WRITE_OWNER,
          AclEntryPermission.SYNCHRONIZE);

  private SqliteBookDirectorySecurity() {}

  static void ensureSecureParentDirectory(Path normalizedBookPath) throws IOException {
    Path parentDirectory =
        SqliteBookFilesystemSupport.requireBookParentDirectory(normalizedBookPath);
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(parentDirectory);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      requireSecureExistingDirectory(normalizedBookPath, parentDirectory);
      return;
    }
    if (SqliteBookFilesystemSupport.supportsPosix(parentDirectory)) {
      Files.createDirectories(
          parentDirectory, PosixFilePermissions.asFileAttribute(POSIX_BOOK_DIRECTORY_PERMISSIONS));
    } else {
      Files.createDirectories(parentDirectory);
    }
    hardenDirectory(parentDirectory);
  }

  static void requireSecureExistingDirectory(Path normalizedBookPath, Path parentDirectory)
      throws IOException {
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedBookPath,
          SqliteCallerPathFailure.PARENT_PATH_COLLISION,
          "The FinGrind SQLite book path requires one real parent directory: "
              + SqliteBookFilesystemSupport.redactedPath(parentDirectory));
    }
    if (SqliteBookFilesystemSupport.supportsPosix(parentDirectory)) {
      requireSecurePosixDirectory(normalizedBookPath, parentDirectory);
      return;
    }
    requireSecureAclDirectory(normalizedBookPath, parentDirectory);
  }

  static void hardenDirectory(Path directoryPath) throws IOException {
    if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (SqliteBookFilesystemSupport.supportsPosix(directoryPath)) {
      Files.setPosixFilePermissions(directoryPath, POSIX_BOOK_DIRECTORY_PERMISSIONS);
      return;
    }
    SqliteBookAclSupport.applyOwnerOnlyAcl(directoryPath, WINDOWS_OWNER_BOOK_DIRECTORY_PERMISSIONS);
  }

  private static void requireSecurePosixDirectory(Path normalizedBookPath, Path parentDirectory)
      throws IOException {
    Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(parentDirectory, LinkOption.NOFOLLOW_LINKS);
    if (!permissions.contains(PosixFilePermission.OWNER_WRITE)
        || !permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
      throw new SqliteCallerPathContractException(
          normalizedBookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "The FinGrind SQLite book parent directory must be owner-writable and owner-searchable: "
              + SqliteBookFilesystemSupport.redactedPath(parentDirectory));
    }
    if (!POSIX_BOOK_DIRECTORY_PERMISSIONS.containsAll(permissions)) {
      throw new SqliteCallerPathContractException(
          normalizedBookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
          "The FinGrind SQLite book parent directory must already use owner-only permissions: "
              + SqliteBookFilesystemSupport.redactedPath(parentDirectory));
    }
  }

  private static void requireSecureAclDirectory(Path normalizedBookPath, Path parentDirectory)
      throws IOException {
    AclFileAttributeView view = SqliteBookAclSupport.aclView(parentDirectory);
    UserPrincipal owner = view.getOwner();
    List<AclEntry> acl = List.copyOf(view.getAcl());
    boolean ownerCanTraverseAndWrite =
        acl.stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> owner.equals(entry.principal()))
            .anyMatch(entry -> entry.permissions().containsAll(ACL_DIRECTORY_REQUIRED_PERMISSIONS));
    if (!ownerCanTraverseAndWrite) {
      throw new SqliteCallerPathContractException(
          normalizedBookPath,
          SqliteCallerPathFailure.PARENT_OWNER_ACCESS_REQUIRED,
          "The FinGrind SQLite book parent directory ACL must grant the directory owner traversal and write access: "
              + SqliteBookFilesystemSupport.redactedPath(parentDirectory));
    }
    acl.stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> !owner.equals(entry.principal()))
        .filter(
            entry ->
                SqliteBookAclSupport.containsAny(
                    entry.permissions(), ACL_DIRECTORY_ACCESS_PERMISSIONS))
        .findFirst()
        .ifPresent(
            entry -> {
              throw new SqliteCallerPathContractException(
                  normalizedBookPath,
                  SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED,
                  "The FinGrind SQLite book parent directory ACL must grant book-directory access only to the directory owner: "
                      + SqliteBookFilesystemSupport.redactedPath(parentDirectory)
                      + " grants access to one non-owner principal.");
            });
  }
}
