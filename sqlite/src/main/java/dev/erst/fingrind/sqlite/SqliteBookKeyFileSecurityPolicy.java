package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Shared owner-only filesystem policy helpers for protected book-key files and directories. */
final class SqliteBookKeyFileSecurityPolicy {
  private SqliteBookKeyFileSecurityPolicy() {}

  static ContractDecision<Path> requireOwnerOnlyPosixPermissions(
      Path path,
      Set<PosixFilePermission> permissions,
      PosixFilePermission requiredPermission,
      Set<PosixFilePermission> allowedPermissions,
      String ownerRequiredMessage,
      String ownerOnlyMessage) {
    if (!permissions.contains(requiredPermission)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(path, ownerRequiredMessage));
    }
    if (!allowedPermissions.containsAll(permissions)) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(path, ownerOnlyMessage));
    }
    return ContractDecision.accepted(path);
  }

  static ContractDecision<Path> requireOwnerOnlyAcl(
      Path path,
      SqliteAclKeyFileSecurity security,
      Set<AclEntryPermission> ownerRequiredPermissions,
      Set<AclEntryPermission> forbiddenNonOwnerPermissions,
      String ownerRequiredMessage,
      String ownerOnlyMessage) {
    if (security.acl().stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> security.owner().equals(entry.principal()))
        .noneMatch(entry -> entry.permissions().containsAll(ownerRequiredPermissions))) {
      return ContractDecision.rejected(
          SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(path, ownerRequiredMessage));
    }
    java.util.Optional<ContractFailure> nonOwnerAccessFailure =
        security.acl().stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !security.owner().equals(entry.principal()))
            .filter(
                entry ->
                    SqliteBookKeyFileSecuritySupport.containsAny(
                        entry.permissions(), forbiddenNonOwnerPermissions))
            .findFirst()
            .map(
                ignoredEntry ->
                    SqliteBookKeyFileSecuritySupport.invalidBookKeyFile(
                        path, ownerOnlyMessage + " It grants access to a non-owner principal."));
    if (nonOwnerAccessFailure.isPresent()) {
      return ContractDecision.rejected(nonOwnerAccessFailure.orElseThrow());
    }
    return ContractDecision.accepted(path);
  }
}
