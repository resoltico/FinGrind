package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Parent-directory validation and hardening for protected book-key files. */
final class SqliteBookKeyFileDirectorySecurity {
  private static final Set<PosixFilePermission> POSIX_KEY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_KEY_DIRECTORY_PERMISSIONS =
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
      Set.of(AclEntryPermission.EXECUTE, AclEntryPermission.LIST_DIRECTORY);
  private static final Set<AclEntryPermission> ACL_SECRET_DIRECTORY_ACCESS_PERMISSIONS =
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

  private SqliteBookKeyFileDirectorySecurity() {}

  static void ensureSecureParentDirectory(Path normalizedPath) throws IOException {
    Path parentDirectory = normalizedPath.getParent();
    if (parentDirectory == null) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
          "The FinGrind book key file must resolve beneath a parent directory.");
    }
    SqliteBookKeyFileSecuritySupport.requireSupportedSecureFilesystem(parentDirectory);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
        throw new SqliteCallerPathContractException(
            normalizedPath,
            SqliteCallerPathFailure.PARENT_PATH_COLLISION,
            "The FinGrind book key file must resolve beneath a real parent directory.");
      }
      requireSecureParentDirectorySecurity(parentDirectory, inspectSecurity(parentDirectory))
          .requireAccepted();
      return;
    }
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(parentDirectory)) {
      Files.createDirectories(
          parentDirectory, PosixFilePermissions.asFileAttribute(POSIX_KEY_DIRECTORY_PERMISSIONS));
    } else {
      Files.createDirectories(parentDirectory);
    }
    hardenDirectory(parentDirectory);
  }

  static void hardenDirectory(Path directoryPath) throws IOException {
    if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(directoryPath)) {
      Files.setPosixFilePermissions(directoryPath, POSIX_KEY_DIRECTORY_PERMISSIONS);
      return;
    }
    SqliteBookKeyFileSecurityPolicy.applyOwnerOnlyAcl(
        directoryPath, WINDOWS_OWNER_KEY_DIRECTORY_PERMISSIONS);
  }

  static boolean hardenExistingOwnerAccessibleDirectory(Path directoryPath) throws IOException {
    if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    return switch (inspectSecurity(directoryPath)) {
      case SqlitePosixKeyFileSecurity posixSecurity ->
          hardenExistingOwnerAccessiblePosixDirectory(directoryPath, posixSecurity.permissions());
      case SqliteAclKeyFileSecurity aclSecurity ->
          hardenExistingOwnerAccessibleAclDirectory(directoryPath, aclSecurity);
    };
  }

  static ContractDecision<Path> requireSecureParentDirectorySecurity(
      Path parentDirectory, SqliteKeyFileSecurity security) {
    return switch (Objects.requireNonNull(security, "security")) {
      case SqlitePosixKeyFileSecurity posixSecurity ->
          requireSecureParentDirectoryPosixPermissions(
              parentDirectory, posixSecurity.permissions());
      case SqliteAclKeyFileSecurity aclSecurity ->
          requireSecureParentDirectoryAcl(parentDirectory, aclSecurity);
    };
  }

  static SqliteKeyFileSecurity inspectSecurity(Path bookKeyFilePath) throws IOException {
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(bookKeyFilePath)) {
      return new SqlitePosixKeyFileSecurity(
          Files.getPosixFilePermissions(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS));
    }
    if (SqliteBookKeyFileSecuritySupport.supportsAcl(bookKeyFilePath)) {
      AclFileAttributeView view = SqliteBookKeyFileSecuritySupport.aclView(bookKeyFilePath);
      return new SqliteAclKeyFileSecurity(view.getOwner(), List.copyOf(view.getAcl()));
    }
    throw new UnsupportedOperationException("no owner-only file security view is available");
  }

  private static ContractDecision<Path> requireSecureParentDirectoryPosixPermissions(
      Path parentDirectory, Set<PosixFilePermission> permissions) {
    return SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyPosixPermissions(
        parentDirectory,
        permissions,
        PosixFilePermission.OWNER_EXECUTE,
        POSIX_KEY_DIRECTORY_PERMISSIONS,
        "The FinGrind book key file parent directory must be owner-searchable.",
        "The FinGrind book key file parent directory must use owner-only permissions.");
  }

  private static ContractDecision<Path> requireSecureParentDirectoryAcl(
      Path parentDirectory, SqliteAclKeyFileSecurity security) {
    return SqliteBookKeyFileSecurityPolicy.requireOwnerOnlyAcl(
        parentDirectory,
        security,
        ACL_DIRECTORY_REQUIRED_PERMISSIONS,
        ACL_SECRET_DIRECTORY_ACCESS_PERMISSIONS,
        "The FinGrind book key file parent directory ACL must grant the directory owner traversal access.",
        "The FinGrind book key file parent directory ACL must grant secret-directory access only to the directory owner.");
  }

  private static boolean hardenExistingOwnerAccessiblePosixDirectory(
      Path directoryPath, Set<PosixFilePermission> permissions) throws IOException {
    if (!permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
      return false;
    }
    if (POSIX_KEY_DIRECTORY_PERMISSIONS.containsAll(permissions)) {
      return false;
    }
    hardenDirectory(directoryPath);
    return true;
  }

  private static boolean hardenExistingOwnerAccessibleAclDirectory(
      Path directoryPath, SqliteAclKeyFileSecurity security) throws IOException {
    boolean ownerCanTraverse =
        security.acl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> security.owner().equals(entry.principal()))
            .anyMatch(entry -> entry.permissions().containsAll(ACL_DIRECTORY_REQUIRED_PERMISSIONS));
    if (!ownerCanTraverse) {
      return false;
    }
    boolean nonOwnerAccessPresent =
        security.acl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !security.owner().equals(entry.principal()))
            .anyMatch(
                entry ->
                    SqliteBookKeyFileSecuritySupport.containsAny(
                        entry.permissions(), ACL_SECRET_DIRECTORY_ACCESS_PERMISSIONS));
    if (!nonOwnerAccessPresent) {
      return false;
    }
    hardenDirectory(directoryPath);
    return true;
  }
}
