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
import java.util.Objects;
import java.util.Set;

/** Enforces owner-only protection for encrypted SQLite book files and their directories. */
final class SqliteBookFileSecurity {
  private static final String POSIX_VIEW = "posix";
  private static final String ACL_VIEW = "acl";
  private static final List<String> SIDECAR_SUFFIXES = List.of("-journal", "-wal", "-shm");
  private static final Set<PosixFilePermission> POSIX_BOOK_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> POSIX_BOOK_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<AclEntryPermission> WINDOWS_OWNER_BOOK_FILE_PERMISSIONS =
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
  private static final Set<AclEntryPermission> WINDOWS_OWNER_BOOK_DIRECTORY_PERMISSIONS =
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

  private SqliteBookFileSecurity() {}

  static void ensureSecureParentDirectory(Path normalizedBookPath) throws IOException {
    Path parentDirectory = requireBookParentDirectory(normalizedBookPath);
    requireSupportedSecureFilesystem(parentDirectory);
    if (supportsPosix(parentDirectory)) {
      Files.createDirectories(
          parentDirectory, PosixFilePermissions.asFileAttribute(POSIX_BOOK_DIRECTORY_PERMISSIONS));
    } else {
      Files.createDirectories(parentDirectory);
    }
    hardenDirectory(parentDirectory);
  }

  static void hardenBookArtifacts(Path normalizedBookPath) throws IOException {
    requireSupportedSecureFilesystem(normalizedBookPath);
    Path parentDirectory = requireBookParentDirectory(normalizedBookPath);
    if (Files.exists(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      hardenDirectory(parentDirectory);
    }
    hardenExistingFile(normalizedBookPath);
    String baseFileName =
        Objects.requireNonNull(normalizedBookPath.getFileName(), "normalizedBookPath fileName")
            .toString();
    for (String suffix : SIDECAR_SUFFIXES) {
      hardenExistingFile(normalizedBookPath.resolveSibling(baseFileName + suffix));
    }
  }

  static void requireSupportedSecureFilesystem(Path path) {
    if (!supportsPosix(path) && !supportsAcl(path)) {
      throw new IllegalStateException(unsupportedSecureFilesystemMessage(path));
    }
  }

  private static void hardenDirectory(Path directoryPath) throws IOException {
    if (!Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (supportsPosix(directoryPath)) {
      Files.setPosixFilePermissions(directoryPath, POSIX_BOOK_DIRECTORY_PERMISSIONS);
      return;
    }
    applyOwnerOnlyAcl(directoryPath, WINDOWS_OWNER_BOOK_DIRECTORY_PERMISSIONS);
  }

  private static void hardenExistingFile(Path filePath) throws IOException {
    if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (supportsPosix(filePath)) {
      Files.setPosixFilePermissions(filePath, POSIX_BOOK_FILE_PERMISSIONS);
      return;
    }
    applyOwnerOnlyAcl(filePath, WINDOWS_OWNER_BOOK_FILE_PERMISSIONS);
  }

  private static void applyOwnerOnlyAcl(Path normalizedPath, Set<AclEntryPermission> permissions)
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

  private static AclFileAttributeView aclView(Path path) {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IllegalStateException(unsupportedSecureFilesystemMessage(path));
    }
    return view;
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(POSIX_VIEW);
  }

  private static boolean supportsAcl(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(ACL_VIEW);
  }

  private static String unsupportedSecureFilesystemMessage(Path path) {
    return "The FinGrind SQLite book file must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs: "
        + path;
  }

  private static Path requireBookParentDirectory(Path normalizedBookPath) {
    Path parentDirectory = normalizedBookPath.getParent();
    if (parentDirectory == null) {
      throw new IllegalArgumentException(
          "The FinGrind SQLite book path must resolve to a file beneath a parent directory: "
              + normalizedBookPath);
    }
    return parentDirectory;
  }
}
