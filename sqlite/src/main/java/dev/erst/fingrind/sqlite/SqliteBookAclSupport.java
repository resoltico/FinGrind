package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;

/** ACL-specific support for owner-only protected-book artifacts. */
final class SqliteBookAclSupport {
  private SqliteBookAclSupport() {}

  static AclFileAttributeView aclView(Path path) {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IllegalStateException(
          SqliteBookFilesystemSupport.unsupportedSecureFilesystemMessage(path));
    }
    return view;
  }

  static void applyOwnerOnlyAcl(Path normalizedPath, Set<AclEntryPermission> permissions)
      throws IOException {
    AclFileAttributeView view = aclView(normalizedPath);
    UserPrincipal owner = view.getOwner();
    AclEntry ownerEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(permissions)
            .build();
    view.setAcl(List.of(ownerEntry));
  }

  static boolean containsAny(
      Set<AclEntryPermission> permissions, Set<AclEntryPermission> requiredPermissions) {
    return requiredPermissions.stream().anyMatch(permissions::contains);
  }
}
