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

/** Enforces platform-native owner-only protection for book-key files. */
final class SqliteBookKeyFileSecurity {
  static final String POSIX_OWNER_READ_WRITE_DESCRIPTOR = "0600";
  static final String WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR = "owner-only-acl";

  private static final String POSIX_VIEW = "posix";
  private static final String ACL_VIEW = "acl";
  private static final Set<PosixFilePermission> POSIX_KEY_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> POSIX_KEY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
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

  private SqliteBookKeyFileSecurity() {}

  static String generatedPermissionsDescriptor(Path normalizedPath) {
    if (supportsPosix(normalizedPath)) {
      return POSIX_OWNER_READ_WRITE_DESCRIPTOR;
    }
    if (supportsAcl(normalizedPath)) {
      return WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR;
    }
    throw unsupportedSecureFilesystem(normalizedPath);
  }

  static void requireSupportedSecureFilesystem(Path normalizedPath) {
    if (!supportsPosix(normalizedPath) && !supportsAcl(normalizedPath)) {
      throw unsupportedSecureFilesystem(normalizedPath);
    }
  }

  static void ensureSecureParentDirectory(Path normalizedPath) throws IOException {
    Path parentDirectory = normalizedPath.getParent();
    if (parentDirectory == null) {
      return;
    }
    if (supportsPosix(parentDirectory)) {
      Files.createDirectories(
          parentDirectory, PosixFilePermissions.asFileAttribute(POSIX_KEY_DIRECTORY_PERMISSIONS));
      return;
    }
    Files.createDirectories(parentDirectory);
  }

  static void createSecureEmptyFile(Path normalizedPath) throws IOException {
    if (supportsPosix(normalizedPath)) {
      Files.createFile(
          normalizedPath, PosixFilePermissions.asFileAttribute(POSIX_KEY_FILE_PERMISSIONS));
      return;
    }
    if (supportsAcl(normalizedPath)) {
      Files.createFile(normalizedPath);
      applyOwnerOnlyAcl(normalizedPath);
      return;
    }
    throw unsupportedSecureFilesystem(normalizedPath);
  }

  static void requireSecureKeyFile(Path bookKeyFilePath) {
    requireSecureKeyFile(bookKeyFilePath, SqliteBookKeyFileSecurity::inspectSecurity);
  }

  static void requireSecureKeyFile(Path bookKeyFilePath, SecurityInspector securityInspector) {
    Objects.requireNonNull(securityInspector, "securityInspector");
    try {
      if (Files.notExists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      if (!Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalStateException(
            "The FinGrind book key file must be a regular non-symlink file: " + bookKeyFilePath);
      }
      requireSecureSecurity(bookKeyFilePath, securityInspector.inspect(bookKeyFilePath));
    } catch (UnsupportedOperationException exception) {
      throw unsupportedSecureFilesystem(bookKeyFilePath, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect the FinGrind book key file permissions: " + bookKeyFilePath,
          exception);
    }
  }

  private static KeyFileSecurity inspectSecurity(Path bookKeyFilePath) throws IOException {
    if (supportsPosix(bookKeyFilePath)) {
      return new PosixSecurity(
          Files.getPosixFilePermissions(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS));
    }
    if (supportsAcl(bookKeyFilePath)) {
      AclFileAttributeView view = aclView(bookKeyFilePath);
      return new AclSecurity(view.getOwner(), List.copyOf(view.getAcl()));
    }
    throw new UnsupportedOperationException("no owner-only file security view is available");
  }

  private static void requireSecureSecurity(Path bookKeyFilePath, KeyFileSecurity security) {
    switch (Objects.requireNonNull(security, "security")) {
      case PosixSecurity posixSecurity ->
          requireSecurePosixPermissions(bookKeyFilePath, posixSecurity.permissions());
      case AclSecurity aclSecurity -> requireSecureAcl(bookKeyFilePath, aclSecurity);
    }
  }

  private static void requireSecurePosixPermissions(
      Path bookKeyFilePath, Set<PosixFilePermission> permissions) {
    if (!permissions.contains(PosixFilePermission.OWNER_READ)) {
      throw new IllegalStateException(
          "The FinGrind book key file must be owner-readable: " + bookKeyFilePath);
    }
    if (!POSIX_KEY_FILE_PERMISSIONS.containsAll(permissions)) {
      throw new IllegalStateException(
          "The FinGrind book key file must use owner-only permissions (0400 or 0600): "
              + bookKeyFilePath);
    }
  }

  private static void requireSecureAcl(Path bookKeyFilePath, AclSecurity security) {
    if (security.acl().stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> security.owner().equals(entry.principal()))
        .noneMatch(entry -> entry.permissions().containsAll(ACL_READ_PERMISSIONS))) {
      throw new IllegalStateException(
          "The FinGrind book key file ACL must grant the file owner read access: "
              + bookKeyFilePath);
    }
    security.acl().stream()
        .filter(entry -> entry.type() == AclEntryType.ALLOW)
        .filter(entry -> !security.owner().equals(entry.principal()))
        .filter(
            entry ->
                !java.util.Collections.disjoint(entry.permissions(), ACL_SECRET_ACCESS_PERMISSIONS))
        .findFirst()
        .ifPresent(
            entry -> {
              throw new IllegalStateException(
                  "The FinGrind book key file ACL must grant secret access only to the file owner: "
                      + bookKeyFilePath
                      + " grants access to "
                      + entry.principal().getName());
            });
  }

  private static void applyOwnerOnlyAcl(Path normalizedPath) throws IOException {
    AclFileAttributeView view = aclView(normalizedPath);
    UserPrincipal owner = view.getOwner();
    AclEntry ownerEntry =
        AclEntry.newBuilder()
            .setType(AclEntryType.ALLOW)
            .setPrincipal(owner)
            .setPermissions(WINDOWS_OWNER_KEY_FILE_PERMISSIONS)
            .build();
    view.setAcl(List.of(ownerEntry));
  }

  private static AclFileAttributeView aclView(Path path) {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new UnsupportedOperationException("no ACL file attribute view is available");
    }
    return view;
  }

  private static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(POSIX_VIEW);
  }

  private static boolean supportsAcl(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(ACL_VIEW);
  }

  private static IllegalStateException unsupportedSecureFilesystem(Path path) {
    return new IllegalStateException(unsupportedSecureFilesystemMessage(path));
  }

  private static IllegalStateException unsupportedSecureFilesystem(
      Path path, RuntimeException cause) {
    return new IllegalStateException(unsupportedSecureFilesystemMessage(path), cause);
  }

  private static String unsupportedSecureFilesystemMessage(Path path) {
    return "The FinGrind book key file must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs: "
        + path;
  }

  /** Reads the native filesystem security descriptor for one key file. */
  @FunctionalInterface
  interface SecurityInspector {
    /** Returns the security model and permissions visible for the supplied path. */
    KeyFileSecurity inspect(Path path) throws IOException;
  }

  /** Platform-native security descriptor supported by the key-file checker. */
  sealed interface KeyFileSecurity permits PosixSecurity, AclSecurity {}

  /** POSIX mode bits for one regular key file. */
  record PosixSecurity(Set<PosixFilePermission> permissions) implements KeyFileSecurity {
    PosixSecurity {
      permissions = Set.copyOf(permissions);
    }
  }

  /** Windows ACL entries for one regular key file, paired with the file owner. */
  record AclSecurity(UserPrincipal owner, List<AclEntry> acl) implements KeyFileSecurity {
    AclSecurity {
      Objects.requireNonNull(owner, "owner");
      acl = List.copyOf(acl);
    }
  }
}
