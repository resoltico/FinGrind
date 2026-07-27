package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Parent-directory validation and atomic creation for protected book-key files. */
final class SqliteBookKeyFileDirectorySecurity {
  private static final Set<PosixFilePermission> POSIX_KEY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
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
      SqlitePrivateOutputDirectoryAdmission.requireOwnerOnlyNonMutableAncestry(
          normalizedPath, parentDirectory);
      return;
    }
    if (!SqliteBookKeyFileSecuritySupport.supportsPosix(parentDirectory)) {
      throw SqlitePrivateOutputDirectoryAdmission.atomicOwnerOnlyDirectoryCreationUnsupported(
          normalizedPath);
    }
    SqlitePrivateOutputDirectoryAdmission.createNewPosixOwnerOnlyDirectories(
        normalizedPath, parentDirectory);
    requireSecureParentDirectorySecurity(parentDirectory, inspectSecurity(parentDirectory))
        .requireAccepted();
    SqlitePrivateOutputDirectoryAdmission.requireOwnerOnlyNonMutableAncestry(
        normalizedPath, parentDirectory);
  }

  static void requireExistingSecureParentDirectory(Path normalizedPath) throws IOException {
    Path parentDirectory = normalizedPath.getParent();
    if (parentDirectory == null || !Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
          "The FinGrind book key file must resolve beneath an existing parent directory.");
    }
    SqliteBookKeyFileSecuritySupport.requireSupportedSecureFilesystem(parentDirectory);
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.PARENT_PATH_COLLISION,
          "The FinGrind book key file must resolve beneath a real parent directory.");
    }
    requireSecureParentDirectorySecurity(parentDirectory, inspectSecurity(parentDirectory))
        .requireAccepted();
    SqlitePrivateOutputDirectoryAdmission.requireOwnerOnlyNonMutableAncestry(
        normalizedPath, parentDirectory);
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
}
