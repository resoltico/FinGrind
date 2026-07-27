package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

/** File and sidecar hardening for protected SQLite book artifacts. */
final class SqliteBookArtifactSecurity {
  private static final Set<PosixFilePermission> POSIX_BOOK_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
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

  private SqliteBookArtifactSecurity() {}

  /**
   * Validates an existing live book without changing any pathname's permissions or ACL.
   *
   * <p>The validation establishes only the security state observed at this path. Portable Java
   * cannot bind it to SQLite's later pathname-based native open across a same-owner rename; it must
   * therefore never be followed by a permission-repair mutation of that pathname.
   */
  static void requireSecureExistingBookFile(Path normalizedBookPath, boolean requiresWrite)
      throws IOException {
    Path checkedBookPath = Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(checkedBookPath);
    Path parentDirectory = SqliteBookFilesystemSupport.requireBookParentDirectory(checkedBookPath);
    SqliteBookDirectorySecurity.requireSecureExistingDirectory(checkedBookPath, parentDirectory);
    requireRegularNonSymlinkBookFile(checkedBookPath);
    if (SqliteBookFilesystemSupport.supportsPosix(checkedBookPath)) {
      requireSecurePosixBookFile(checkedBookPath, requiresWrite);
      return;
    }
    requireSecureAclBookFile(checkedBookPath, requiresWrite);
  }

  /**
   * Atomically creates one empty private live-book file on the selected exact path.
   *
   * <p>Portable Java can attach POSIX {@code 0600} permissions to the same {@code CREATE_NEW}
   * operation that claims the name. It cannot do the equivalent for ACL-only filesystems, so this
   * method fails closed instead of creating a readable book and repairing its ACL afterwards.
   */
  static void createNewOwnerOnlyBookFile(Path normalizedBookPath) throws IOException {
    Path checkedBookPath = Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(checkedBookPath);
    Path parentDirectory = SqliteBookFilesystemSupport.requireBookParentDirectory(checkedBookPath);
    SqliteBookDirectorySecurity.requireSecureExistingDirectory(checkedBookPath, parentDirectory);
    if (!SqliteBookFilesystemSupport.supportsPosix(checkedBookPath)) {
      throw atomicOwnerOnlyBookCreationUnsupported(checkedBookPath, null);
    }
    try (FileChannel channel =
        FileChannel.open(
            checkedBookPath,
            Set.<OpenOption>of(
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW,
                LinkOption.NOFOLLOW_LINKS),
            PosixFilePermissions.asFileAttribute(POSIX_BOOK_FILE_PERMISSIONS))) {
      if (channel.size() != 0L) {
        throw new IOException("A newly created FinGrind SQLite book file was not empty.");
      }
    } catch (UnsupportedOperationException unsupported) {
      throw atomicOwnerOnlyBookCreationUnsupported(checkedBookPath, unsupported);
    }
  }

  private static void requireRegularNonSymlinkBookFile(Path normalizedBookPath) {
    if (Files.isRegularFile(normalizedBookPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    throw new SqliteCallerPathContractException(
        normalizedBookPath,
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "The FinGrind SQLite book path must resolve to an existing regular non-symlink file: "
            + SqliteBookFilesystemSupport.absolutePath(normalizedBookPath));
  }

  private static void requireSecurePosixBookFile(Path normalizedBookPath, boolean requiresWrite)
      throws IOException {
    Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(normalizedBookPath, LinkOption.NOFOLLOW_LINKS);
    if (!permissions.contains(PosixFilePermission.OWNER_READ)
        || (requiresWrite && !permissions.contains(PosixFilePermission.OWNER_WRITE))
        || !POSIX_BOOK_FILE_PERMISSIONS.containsAll(permissions)) {
      throw ownerOnlyBookFileRequired(normalizedBookPath);
    }
  }

  private static void requireSecureAclBookFile(Path normalizedBookPath, boolean requiresWrite)
      throws IOException {
    AclFileAttributeView view = SqliteBookAclSupport.aclView(normalizedBookPath);
    UserPrincipal owner = view.getOwner();
    List<AclEntry> acl = List.copyOf(view.getAcl());
    Set<AclEntryPermission> ownerRequiredPermissions =
        requiresWrite ? WINDOWS_OWNER_BOOK_FILE_PERMISSIONS : Set.of(AclEntryPermission.READ_DATA);
    boolean ownerAuthorized =
        acl.stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> owner.equals(entry.principal()))
            .anyMatch(entry -> entry.permissions().containsAll(ownerRequiredPermissions));
    boolean nonOwnerAccess =
        acl.stream()
            .filter(entry -> entry.type() == AclEntryType.ALLOW)
            .filter(entry -> !owner.equals(entry.principal()))
            .anyMatch(
                entry ->
                    SqliteBookAclSupport.containsAny(
                        entry.permissions(), WINDOWS_OWNER_BOOK_FILE_PERMISSIONS));
    if (!ownerAuthorized || nonOwnerAccess) {
      throw ownerOnlyBookFileRequired(normalizedBookPath);
    }
  }

  private static SqliteCallerPathContractException ownerOnlyBookFileRequired(
      Path normalizedBookPath) {
    return new SqliteCallerPathContractException(
        normalizedBookPath,
        SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
        "The FinGrind SQLite book file must already use owner-only permissions: "
            + SqliteBookFilesystemSupport.absolutePath(normalizedBookPath));
  }

  private static SqliteCallerPathContractException atomicOwnerOnlyBookCreationUnsupported(
      Path normalizedBookPath, @org.jspecify.annotations.Nullable Throwable cause) {
    String message =
        "The selected filesystem cannot atomically create one owner-only FinGrind SQLite book file.";
    return cause == null
        ? new SqliteCallerPathContractException(
            normalizedBookPath,
            SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
            message)
        : new SqliteCallerPathContractException(
            normalizedBookPath,
            SqliteCallerPathFailure.ATOMIC_OWNER_ONLY_PROTOCOL_FILE_CREATION_UNSUPPORTED,
            message,
            cause);
  }
}
