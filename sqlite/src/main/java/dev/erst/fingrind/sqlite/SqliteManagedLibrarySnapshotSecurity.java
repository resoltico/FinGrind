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

/** Private filesystem protection for verified managed-library snapshots. */
final class SqliteManagedLibrarySnapshotSecurity {
  private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
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
  private static final Set<AclEntryPermission> WINDOWS_OWNER_FILE_PERMISSIONS =
      Set.of(
          AclEntryPermission.READ_DATA,
          AclEntryPermission.WRITE_DATA,
          AclEntryPermission.APPEND_DATA,
          AclEntryPermission.EXECUTE,
          AclEntryPermission.READ_NAMED_ATTRS,
          AclEntryPermission.WRITE_NAMED_ATTRS,
          AclEntryPermission.READ_ATTRIBUTES,
          AclEntryPermission.WRITE_ATTRIBUTES,
          AclEntryPermission.DELETE,
          AclEntryPermission.READ_ACL,
          AclEntryPermission.SYNCHRONIZE);

  private SqliteManagedLibrarySnapshotSecurity() {}

  static Path createPrivateSnapshotDirectory() {
    Path tempRoot =
        SqliteManagedLibraryDigestSupport.normalizedLibraryPath(
            Path.of(System.getProperty("java.io.tmpdir")));
    return createPrivateSnapshotDirectory(tempRoot, supportsPosix(tempRoot));
  }

  static Path createPrivateSnapshotDirectory(Path tempRoot, boolean supportsPosix) {
    Path normalizedTempRoot = SqliteManagedLibraryDigestSupport.normalizedLibraryPath(tempRoot);
    try {
      if (supportsPosix) {
        return Files.createTempDirectory(
            normalizedTempRoot,
            "fingrind-managed-sqlite-",
            PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY_PERMISSIONS));
      }
      Path snapshotDirectory =
          Files.createTempDirectory(normalizedTempRoot, "fingrind-managed-sqlite-");
      hardenPrivateDirectory(snapshotDirectory);
      return snapshotDirectory;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to create a private managed SQLite verification snapshot directory.", exception);
    }
  }

  static void hardenPrivateDirectory(Path directory) {
    try {
      if (supportsPosix(directory)) {
        Files.setPosixFilePermissions(directory, PRIVATE_DIRECTORY_PERMISSIONS);
        return;
      }
      if (supportsAcl(directory)) {
        applyOwnerOnlyAcl(directory, WINDOWS_OWNER_DIRECTORY_PERMISSIONS);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to apply private managed SQLite snapshot directory permissions at "
              + directory
              + ".",
          exception);
    }
  }

  static void hardenPrivateFile(Path file) {
    try {
      if (supportsPosix(file)) {
        Files.setPosixFilePermissions(file, PRIVATE_FILE_PERMISSIONS);
        return;
      }
      if (supportsAcl(file)) {
        applyOwnerOnlyAcl(file, WINDOWS_OWNER_FILE_PERMISSIONS);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to apply private managed SQLite snapshot permissions at " + file + ".",
          exception);
    }
  }

  static void registerDeleteOnExit(Path path) {
    try {
      path.toFile().deleteOnExit();
    } catch (UnsupportedOperationException | SecurityException ignored) {
      // Delete-on-exit is best-effort only. The verified snapshot still has explicit cleanup paths.
    }
  }

  private static boolean supportsPosix(Path path) {
    try {
      return Files.getFileStore(path).supportsFileAttributeView("posix");
    } catch (IOException exception) {
      return false;
    }
  }

  private static boolean supportsAcl(Path path) {
    try {
      return Files.getFileStore(path).supportsFileAttributeView("acl");
    } catch (IOException exception) {
      return false;
    }
  }

  private static void applyOwnerOnlyAcl(Path normalizedPath, Set<AclEntryPermission> permissions)
      throws IOException {
    AclFileAttributeView view =
        Files.getFileAttributeView(
            normalizedPath, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IllegalStateException(
          "Owner-only ACLs are unavailable for the managed SQLite snapshot path "
              + normalizedPath
              + ".");
    }
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
