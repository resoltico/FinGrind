package dev.erst.fingrind.sqlite;

import static dev.erst.fingrind.sqlite.SqliteBookKeyFileSecuritySupport.redactedPath;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
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

/** Key-file artifact validation and owner-only file hardening. */
final class SqliteBookKeyFileArtifactSecurity {
  private static final Set<PosixFilePermission> POSIX_KEY_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_KEY_FILE_PERMISSIONS =
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
  private static final Set<AclEntryPermission> ACL_READ_PERMISSIONS =
      Set.of(AclEntryPermission.READ_DATA);
  private static final Set<AclEntryPermission> ACL_SECRET_ACCESS_PERMISSIONS =
      Set.of(
          AclEntryPermission.READ_DATA,
          AclEntryPermission.WRITE_DATA,
          AclEntryPermission.APPEND_DATA,
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

  private SqliteBookKeyFileArtifactSecurity() {}

  static void createSecureEmptyFile(Path normalizedPath) throws IOException {
    if (SqliteBookKeyFileSecuritySupport.supportsPosix(normalizedPath)) {
      Files.createFile(
          normalizedPath, PosixFilePermissions.asFileAttribute(POSIX_KEY_FILE_PERMISSIONS));
      return;
    }
    if (SqliteBookKeyFileSecuritySupport.supportsAcl(normalizedPath)) {
      Files.createFile(normalizedPath);
      applyOwnerOnlyAcl(normalizedPath, WINDOWS_OWNER_KEY_FILE_PERMISSIONS);
      return;
    }
    throw new IllegalStateException(
        SqliteBookKeyFileSecuritySupport.unsupportedSecureFilesystemMessage(normalizedPath));
  }

  static ContractDecision<Path> requireSecureKeyFile(Path bookKeyFilePath) {
    return requireSecureKeyFile(
        bookKeyFilePath, SqliteBookKeyFileDirectorySecurity::inspectSecurity);
  }

  static ContractDecision<Path> requireSecureKeyFile(
      Path bookKeyFilePath, SqliteKeyFileSecurityInspector securityInspector) {
    Objects.requireNonNull(securityInspector, "securityInspector");
    try {
      if (Files.notExists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.rejected(
            SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                "The FinGrind book key file does not exist: " + redactedPath(bookKeyFilePath)));
      }
      if (!Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.rejected(
            SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                "The FinGrind book key file must be a regular non-symlink file: "
                    + redactedPath(bookKeyFilePath)));
      }
      Path parentDirectory =
          SqliteBookKeyFileSecuritySupport.requireKeyFileParentDirectory(bookKeyFilePath);
      ContractDecision<Path> parentDirectoryDecision =
          SqliteBookKeyFileDirectorySecurity.requireSecureParentDirectorySecurity(
              parentDirectory, securityInspector.inspect(parentDirectory));
      return switch (parentDirectoryDecision) {
        case ContractDecision.Accepted<Path> _ ->
            requireSecureSecurity(bookKeyFilePath, securityInspector.inspect(bookKeyFilePath));
        case ContractDecision.Rejected<Path>(ContractFailure failure) ->
            ContractDecision.rejected(failure);
      };
    } catch (UnsupportedOperationException exception) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.unsupportedSecureFilesystem(bookKeyFilePath, exception));
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(
              "Failed to inspect the FinGrind book key file permissions: "
                  + redactedPath(bookKeyFilePath),
              "Inspect the selected book key file path, permissions, and filesystem accessibility, then rerun the command.",
              null));
    }
  }

  private static ContractDecision<Path> requireSecureSecurity(
      Path bookKeyFilePath, SqliteKeyFileSecurity security) {
    return switch (Objects.requireNonNull(security, "security")) {
      case SqlitePosixKeyFileSecurity posixSecurity ->
          requireSecurePosixPermissions(bookKeyFilePath, posixSecurity.permissions());
      case SqliteAclKeyFileSecurity aclSecurity -> requireSecureAcl(bookKeyFilePath, aclSecurity);
    };
  }

  private static ContractDecision<Path> requireSecurePosixPermissions(
      Path bookKeyFilePath, Set<PosixFilePermission> permissions) {
    if (!permissions.contains(PosixFilePermission.OWNER_READ)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file must be owner-readable: "
                  + redactedPath(bookKeyFilePath)));
    }
    if (!POSIX_KEY_FILE_PERMISSIONS.containsAll(permissions)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file must use owner-only permissions (0400 or 0600): "
                  + redactedPath(bookKeyFilePath)));
    }
    return ContractDecision.accepted(bookKeyFilePath);
  }

  private static ContractDecision<Path> requireSecureAcl(
      Path bookKeyFilePath, SqliteAclKeyFileSecurity security) {
    if (security.acl().stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> security.owner().equals(entry.principal()))
        .noneMatch(entry -> entry.permissions().containsAll(ACL_READ_PERMISSIONS))) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
              "The FinGrind book key file ACL must grant the file owner read access: "
                  + redactedPath(bookKeyFilePath)));
    }
    java.util.Optional<ContractFailure> nonOwnerAccessFailure =
        security.acl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !security.owner().equals(entry.principal()))
            .filter(
                entry ->
                    !java.util.Collections.disjoint(
                        entry.permissions(), ACL_SECRET_ACCESS_PERMISSIONS))
            .findFirst()
            .map(
                entry ->
                    SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                        "The FinGrind book key file ACL must grant secret access only to the file owner: "
                            + redactedPath(bookKeyFilePath)
                            + " grants access to one non-owner principal."));
    if (nonOwnerAccessFailure.isPresent()) {
      return ContractDecision.rejected(nonOwnerAccessFailure.orElseThrow());
    }
    return ContractDecision.accepted(bookKeyFilePath);
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
