package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Evaluates POSIX and ACL ownership evidence for a private output-directory namespace. */
final class PrivateOutputDirectorySecurity {
  private static final Set<PosixFilePermission> PRIVATE_POSIX_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<AclEntryPermission> REQUIRED_OWNER_ACL_PERMISSIONS =
      Set.of(
          AclEntryPermission.LIST_DIRECTORY,
          AclEntryPermission.ADD_FILE,
          AclEntryPermission.EXECUTE);
  private static final Set<AclEntryPermission> NON_OWNER_DIRECTORY_ACCESS_PERMISSIONS =
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

  private PrivateOutputDirectorySecurity() {}

  static Set<PosixFilePermission> privatePosixDirectoryPermissions() {
    return PRIVATE_POSIX_DIRECTORY_PERMISSIONS;
  }

  static OutputDirectorySecurityIdentity requirePrivateDirectory(
      Path directory, PrivateOutputDirectory.FilesystemAccess filesystemAccess) throws IOException {
    boolean accessModelAvailable = false;
    PrivateOutputDirectory.@Nullable PosixDirectoryIdentity posixIdentity = null;
    @Nullable UserPrincipal aclOwner = null;
    if (filesystemAccess.supportsPosix(directory)) {
      requirePrivatePosixDirectory(directory, filesystemAccess.readPosixPermissions(directory));
      posixIdentity = filesystemAccess.readPosixDirectoryIdentity(directory);
      accessModelAvailable = true;
    }
    if (filesystemAccess.supportsAcl(directory)) {
      PrivateOutputDirectory.AclState aclState = filesystemAccess.readAcl(directory);
      requirePrivateAclDirectory(directory, aclState, filesystemAccess);
      aclOwner = aclState.owner();
      accessModelAvailable = true;
    }
    if (!accessModelAvailable) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory,
          "must live on a filesystem supporting POSIX owner-only permissions or owner-only ACLs");
    }
    return new OutputDirectorySecurityIdentity(posixIdentity, aclOwner);
  }

  static void requireExistingCreationAncestry(
      Path existingAncestor, PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    Path descendant = existingAncestor;
    int ancestryDepth = 0;
    while (descendant != null) {
      if (!filesystemAccess.isDirectoryNoFollow(descendant)) {
        throw PrivateOutputDirectoryFailures.requirement(
            descendant, "must remain a real directory in the output creation ancestry");
      }
      boolean accessModelAvailable = false;
      if (filesystemAccess.supportsPosix(descendant)) {
        requireNoUnprotectedPosixMutation(descendant, filesystemAccess);
        accessModelAvailable = true;
      }
      if (filesystemAccess.supportsAcl(descendant)) {
        PrivateOutputDirectory.AclState aclState = filesystemAccess.readAcl(descendant);
        PrivateOutputDirectoryAclMutationSecurity.requireAclMutationDeniedExcept(
            descendant,
            aclState,
            Set.copyOf(
                filesystemAccess.permittedAclMutationPrincipalsForCreation(descendant, aclState)),
            filesystemAccess,
            aclMutationRequirement("output creation ancestry", "CREATION", ancestryDepth));
        accessModelAvailable = true;
      }
      if (!accessModelAvailable) {
        throw PrivateOutputDirectoryFailures.requirement(
            descendant,
            "must live on a filesystem supporting POSIX permissions or ACLs throughout the output creation ancestry");
      }
      descendant = filesystemAccess.parent(descendant);
      ancestryDepth++;
    }
  }

  static void requireProtectedAncestry(
      Path directory,
      OutputDirectorySecurityIdentity outputIdentity,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    Path descendant = directory;
    @Nullable Path ancestor = filesystemAccess.parent(directory);
    int ancestryDepth = 1;
    while (ancestor != null) {
      Path checkedAncestor = ancestor;
      if (!filesystemAccess.isDirectoryNoFollow(checkedAncestor)) {
        throw PrivateOutputDirectoryFailures.requirement(
            checkedAncestor, "must remain a real directory in the output ancestry");
      }
      boolean accessModelAvailable = false;
      if (filesystemAccess.supportsPosix(checkedAncestor)) {
        PrivateOutputDirectory.@Nullable PosixDirectoryIdentity outputPosixIdentity =
            outputIdentity.posixIdentity();
        if (outputPosixIdentity == null) {
          throw PrivateOutputDirectoryFailures.requirement(
              checkedAncestor,
              "must share the output directory's POSIX access model throughout the output ancestry");
        }
        requireOutputOwnerPosixMutationDenied(
            checkedAncestor,
            filesystemAccess.readPosixPermissions(checkedAncestor),
            descendant,
            outputPosixIdentity,
            filesystemAccess);
        accessModelAvailable = true;
      }
      if (filesystemAccess.supportsAcl(checkedAncestor)) {
        @Nullable UserPrincipal outputAclOwner = outputIdentity.aclOwner();
        if (outputAclOwner == null) {
          throw PrivateOutputDirectoryFailures.requirement(
              checkedAncestor,
              "must share the output directory's ACL access model throughout the output ancestry");
        }
        PrivateOutputDirectoryAclMutationSecurity.requireOutputOwnerAclMutationDenied(
            checkedAncestor,
            filesystemAccess.readAcl(checkedAncestor),
            outputAclOwner,
            ancestryDepth,
            filesystemAccess);
        accessModelAvailable = true;
      }
      if (!accessModelAvailable) {
        throw PrivateOutputDirectoryFailures.requirement(
            checkedAncestor,
            "must live on a filesystem supporting POSIX permissions or ACLs throughout the output ancestry");
      }
      descendant = checkedAncestor;
      ancestor = filesystemAccess.parent(checkedAncestor);
      ancestryDepth++;
    }
  }

  private static void requirePrivatePosixDirectory(
      Path directory, Set<PosixFilePermission> permissions)
      throws PrivateOutputDirectory.Violation {
    if (!permissions.contains(PosixFilePermission.OWNER_WRITE)
        || !permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory, "must be owner-writable and owner-searchable");
    }
    if (!PRIVATE_POSIX_DIRECTORY_PERMISSIONS.containsAll(permissions)) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory, "must grant no directory access to group or other principals");
    }
  }

  private static void requirePrivateAclDirectory(
      Path directory,
      PrivateOutputDirectory.AclState aclState,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    requireOwnerAclPermissions(directory, aclState, filesystemAccess);
    requireNoNonOwnerAclDirectoryAccess(directory, aclState, filesystemAccess);
  }

  private static void requireOwnerAclPermissions(
      Path directory,
      PrivateOutputDirectory.AclState aclState,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    boolean ownerCanWriteAndTraverse = false;
    for (AclEntry entry : aclState.entries()) {
      if (entry.type() == AclEntryType.ALLOW
          && isEffectiveOnDirectory(entry)
          && filesystemAccess.matchesAclPrincipalIdentity(
              directory, aclState.owner(), entry.principal())
          && entry.permissions().containsAll(REQUIRED_OWNER_ACL_PERMISSIONS)) {
        ownerCanWriteAndTraverse = true;
        break;
      }
    }
    if (!ownerCanWriteAndTraverse) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory, "must grant its owner directory traversal and write access");
    }
  }

  private static void requireNoNonOwnerAclDirectoryAccess(
      Path directory,
      PrivateOutputDirectory.AclState aclState,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    for (AclEntry entry : aclState.entries()) {
      if (entry.type() == AclEntryType.ALLOW
          && isEffectiveOnDirectory(entry)
          && !filesystemAccess.matchesAclPrincipalIdentity(
              directory, aclState.owner(), entry.principal())
          && !filesystemAccess.isTrustedAclMutationPrincipal(directory, entry.principal())
          && hasAnyPermission(entry, NON_OWNER_DIRECTORY_ACCESS_PERMISSIONS)) {
        throw PrivateOutputDirectoryFailures.requirement(
            directory, "must grant directory access only to its owner");
      }
    }
  }

  private static void requireOutputOwnerPosixMutationDenied(
      Path directory,
      Set<PosixFilePermission> permissions,
      Path descendant,
      PrivateOutputDirectory.PosixDirectoryIdentity outputIdentity,
      PrivateOutputDirectory.FilesystemAccess filesystemAccess)
      throws IOException {
    PrivateOutputDirectory.PosixDirectoryIdentity ancestorIdentity =
        filesystemAccess.readPosixDirectoryIdentity(directory);
    if (!ancestorIdentity.owner().equals(outputIdentity.owner())
        && ancestorIdentity.unixUserId() != 0L) {
      throw PrivateOutputDirectoryFailures.requirement(
          directory, "must be owned by the output-directory owner or POSIX superuser");
    }
    if (!permissions.contains(PosixFilePermission.GROUP_WRITE)
        && !permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
      return;
    }
    PrivateOutputDirectory.PosixDirectoryIdentity descendantIdentity =
        filesystemAccess.readPosixDirectoryIdentity(descendant);
    if (ancestorIdentity.sticky() && outputIdentity.owner().equals(descendantIdentity.owner())) {
      return;
    }
    throw PrivateOutputDirectoryFailures.requirement(
        directory,
        "must deny group and other mutation in the output ancestry unless it is sticky and its child is owned by the output-directory owner");
  }

  private static void requireNoUnprotectedPosixMutation(
      Path directory, PrivateOutputDirectory.FilesystemAccess filesystemAccess) throws IOException {
    Set<PosixFilePermission> permissions = filesystemAccess.readPosixPermissions(directory);
    if ((!permissions.contains(PosixFilePermission.GROUP_WRITE)
            && !permissions.contains(PosixFilePermission.OTHERS_WRITE))
        || filesystemAccess.readPosixDirectoryIdentity(directory).sticky()) {
      return;
    }
    throw PrivateOutputDirectoryFailures.requirement(
        directory,
        "must deny group and other mutation in the output creation ancestry unless it is a sticky POSIX directory");
  }

  private static String aclMutationRequirement(String location, String scope, int ancestryDepth) {
    return "must deny non-owner mutation in the "
        + location
        + " [FINGRIND_ACL_MUTATION_SCOPE="
        + scope
        + "] [FINGRIND_ACL_ANCESTRY_DEPTH="
        + ancestryDepth
        + "]";
  }

  private static boolean hasAnyPermission(AclEntry entry, Set<AclEntryPermission> permissions) {
    return permissions.stream().anyMatch(entry.permissions()::contains);
  }

  private static boolean isEffectiveOnDirectory(AclEntry entry) {
    return !entry.flags().contains(AclEntryFlag.INHERIT_ONLY);
  }

  record OutputDirectorySecurityIdentity(
      PrivateOutputDirectory.@Nullable PosixDirectoryIdentity posixIdentity,
      @Nullable UserPrincipal aclOwner) {}
}
