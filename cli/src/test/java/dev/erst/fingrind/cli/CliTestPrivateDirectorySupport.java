package dev.erst.fingrind.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;

/** Shared helpers for allocating owner-only temporary directories in CLI tests. */
final class CliTestPrivateDirectorySupport {
  private static final Set<PosixFilePermission> POSIX_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_DIRECTORY_PERMISSIONS =
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

  private CliTestPrivateDirectorySupport() {}

  static void hardenOwnerOnlyDirectory(Path directoryPath) {
    try {
      if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      if (directoryPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(directoryPath, POSIX_DIRECTORY_PERMISSIONS);
        return;
      }
      AclFileAttributeView view =
          Files.getFileAttributeView(
              directoryPath, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
      if (view == null) {
        return;
      }
      UserPrincipal owner = view.getOwner();
      AclEntry ownerEntry =
          AclEntry.newBuilder()
              .setType(AclEntryType.ALLOW)
              .setPrincipal(owner)
              .setPermissions(WINDOWS_OWNER_DIRECTORY_PERMISSIONS)
              .build();
      view.setAcl(List.of(ownerEntry));
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to harden one CLI test temporary directory: " + directoryPath, exception);
    }
  }
}
