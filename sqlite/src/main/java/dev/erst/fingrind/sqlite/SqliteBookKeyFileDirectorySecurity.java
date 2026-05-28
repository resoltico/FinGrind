package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqliteBookKeyFileSecuritySupport.redactedPath;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
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
      return;
    }
    SqliteBookKeyFileSecuritySupport.requireSupportedSecureFilesystem(parentDirectory);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalArgumentException(
            "The FinGrind book key file must resolve beneath an existing directory: "
                + redactedPath(normalizedPath));
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
    applyOwnerOnlyAcl(directoryPath, WINDOWS_OWNER_KEY_DIRECTORY_PERMISSIONS);
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
    if (!permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file parent directory must be owner-searchable: "
                  + redactedPath(parentDirectory)));
    }
    if (!POSIX_KEY_DIRECTORY_PERMISSIONS.containsAll(permissions)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file parent directory must use owner-only permissions: "
                  + redactedPath(parentDirectory)));
    }
    return ContractDecision.accepted(parentDirectory);
  }

  private static ContractDecision<Path> requireSecureParentDirectoryAcl(
      Path parentDirectory, SqliteAclKeyFileSecurity security) {
    if (security.acl().stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> security.owner().equals(entry.principal()))
        .noneMatch(entry -> entry.permissions().containsAll(ACL_DIRECTORY_REQUIRED_PERMISSIONS))) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file parent directory ACL must grant the directory owner traversal access: "
                  + redactedPath(parentDirectory)));
    }
    java.util.Optional<ContractFailure> nonOwnerAccessFailure =
        security.acl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !security.owner().equals(entry.principal()))
            .filter(
                entry ->
                    SqliteBookKeyFileSecuritySupport.containsAny(
                        entry.permissions(), ACL_SECRET_DIRECTORY_ACCESS_PERMISSIONS))
            .findFirst()
            .map(
                entry ->
                    SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                        "The FinGrind book key file parent directory ACL must grant secret-directory access only to the directory owner: "
                            + redactedPath(parentDirectory)
                            + " grants access to one non-owner principal."));
    if (nonOwnerAccessFailure.isPresent()) {
      return ContractDecision.rejected(nonOwnerAccessFailure.orElseThrow());
    }
    return ContractDecision.accepted(parentDirectory);
  }

  private static void applyOwnerOnlyAcl(Path normalizedPath, Set<AclEntryPermission> permissions)
      throws IOException {
    AclFileAttributeView view = SqliteBookKeyFileSecuritySupport.aclView(normalizedPath);
    UserPrincipal owner = view.getOwner();
    AclEntry ownerEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(permissions)
            .build();
    view.setAcl(List.of(ownerEntry));
  }
}
