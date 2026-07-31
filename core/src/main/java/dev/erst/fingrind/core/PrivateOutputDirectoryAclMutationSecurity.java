package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates whether ACL entries can let non-owners mutate a protected output namespace. */
final class PrivateOutputDirectoryAclMutationSecurity {
  private static final Set<AclEntryPermission> NON_OWNER_DIRECTORY_MUTATION_PERMISSIONS =
      Set.of(
          AclEntryPermission.DELETE_CHILD,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.WRITE_ACL,
          AclEntryPermission.WRITE_OWNER);

  private PrivateOutputDirectoryAclMutationSecurity() {}

  static void requireOutputOwnerAclMutationDenied(
      Path directory,
      PrivateOutputDirectory.AclState aclState,
      UserPrincipal outputOwner,
      int ancestryDepth,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    if (!filesystemAccess.matchesAclPrincipalIdentity(directory, aclState.owner(), outputOwner)
        && !filesystemAccess.isTrustedAclMutationPrincipal(directory, aclState.owner())) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory,
          "must be owned by the output-directory owner or a trusted operating-system principal and deny non-owner mutation in the output ancestry");
    }
    requireAclMutationDeniedExcept(
        directory,
        aclState,
        Set.of(outputOwner),
        filesystemAccess,
        mutationRequirement("output ancestry", "PROTECTED", ancestryDepth));
  }

  static void requireAclMutationDeniedExcept(
      Path directory,
      PrivateOutputDirectory.AclState aclState,
      Set<UserPrincipal> permittedPrincipals,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess,
      String requirement)
      throws IOException {
    for (AclEntry entry : aclState.entries()) {
      if (entry.type() != AclEntryType.ALLOW || !isEffectiveOnDirectory(entry)) {
        continue;
      }
      boolean principalPermitted = false;
      for (UserPrincipal permittedPrincipal : permittedPrincipals) {
        if (filesystemAccess.matchesAclPrincipalIdentity(
            directory, permittedPrincipal, entry.principal())) {
          principalPermitted = true;
          break;
        }
      }
      if (!principalPermitted
          && !filesystemAccess.isTrustedAclMutationPrincipal(directory, entry.principal())
          && hasAnyPermission(entry, NON_OWNER_DIRECTORY_MUTATION_PERMISSIONS)) {
        throw PrivateOutputDirectoryFailures.requirement(
            directory,
            requirement
                + " [FINGRIND_ACL_MUTATION_PERMISSIONS="
                + grantedMutationPermissions(entry)
                + "] [FINGRIND_ACL_MUTATION_PRINCIPAL="
                + filesystemAccess.classifyAclMutationPrincipal(directory, entry.principal())
                + "]");
      }
    }
  }

  private static String mutationRequirement(String location, String scope, int ancestryDepth) {
    return "must deny non-owner mutation in the "
        + location
        + " [FINGRIND_ACL_MUTATION_SCOPE="
        + scope
        + "] [FINGRIND_ACL_ANCESTRY_DEPTH="
        + ancestryDepth
        + "]";
  }

  private static String grantedMutationPermissions(AclEntry entry) {
    return entry.permissions().stream()
        .filter(NON_OWNER_DIRECTORY_MUTATION_PERMISSIONS::contains)
        .map(Enum::name)
        .sorted()
        .collect(Collectors.joining(","));
  }

  private static boolean hasAnyPermission(AclEntry entry, Set<AclEntryPermission> permissions) {
    return permissions.stream().anyMatch(entry.permissions()::contains);
  }

  private static boolean isEffectiveOnDirectory(AclEntry entry) {
    return !entry.flags().contains(AclEntryFlag.INHERIT_ONLY);
  }
}
