package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
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
import java.util.Objects;
import java.util.Set;

/** File and sidecar admission for protected SQLite book artifacts. */
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
   * <p>The shared core capability retains and admits the exact nofollow descriptor. SQLite still
   * opens by pathname later, so this remains an admission check rather than a loaded-image proof.
   */
  static void requireSecureExistingBookFile(Path normalizedBookPath, boolean requiresWrite)
      throws IOException {
    Path checkedBookPath = Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(checkedBookPath);
    Path parentDirectory = SqliteBookFilesystemSupport.requireBookParentDirectory(checkedBookPath);
    SqliteBookDirectorySecurity.requireSecureExistingDirectory(checkedBookPath, parentDirectory);
    requireExistingBookAccessPolicy(checkedBookPath, requiresWrite);
    requireNoUnexpectedWalResidue(checkedBookPath);
    try {
      PrivateOutputFile.requireExistingOwnerOnly(
          checkedBookPath,
          requiresWrite ? PrivateOutputFile.Access.READ_WRITE : PrivateOutputFile.Access.READ_ONLY);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(checkedBookPath, violation);
    }
  }

  private static void requireNoUnexpectedWalResidue(Path bookPath) {
    requireAbsentWalSidecar(bookPath, "-wal");
    requireAbsentWalSidecar(bookPath, "-shm");
  }

  private static void requireAbsentWalSidecar(Path bookPath, String suffix) {
    Path sidecar = bookPath.resolveSibling(bookPath.getFileName() + suffix);
    if (Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteProtectedBookVerificationException(
          new IllegalStateException(
              "The protected book has unexpected WAL-mode sidecar residue: "
                  + SqliteBookFilesystemSupport.absolutePath(sidecar)));
    }
  }

  /** Atomically creates one empty private live-book file through the shared core capability. */
  static void createNewOwnerOnlyBookFile(Path normalizedBookPath) throws IOException {
    Path checkedBookPath = Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    SqliteBookFilesystemSupport.requireSupportedSecureFilesystem(checkedBookPath);
    Path parentDirectory = SqliteBookFilesystemSupport.requireBookParentDirectory(checkedBookPath);
    SqliteBookDirectorySecurity.requireSecureExistingDirectory(checkedBookPath, parentDirectory);
    try (PrivateOutputFile.OpenedFile channel = PrivateOutputFile.createNew(checkedBookPath)) {
      if (channel.size() != 0L) {
        throw new IOException("A newly created FinGrind SQLite book file was not empty.");
      }
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(checkedBookPath, violation);
    }
  }

  /** Verifies SQLite's file-access policy before the core binds an exact opened descriptor. */
  private static void requireExistingBookAccessPolicy(Path bookPath, boolean requiresWrite)
      throws IOException {
    requireRegularNonSymlinkBookFile(bookPath);
    if (SqliteBookFilesystemSupport.supportsPosix(bookPath)) {
      requireSecurePosixBookFile(bookPath, requiresWrite);
      return;
    }
    requireSecureAclBookFile(bookPath, requiresWrite);
  }

  private static void requireRegularNonSymlinkBookFile(Path bookPath) {
    if (Files.isRegularFile(bookPath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    throw new SqliteCallerPathContractException(
        bookPath,
        SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
        "The FinGrind SQLite book path must resolve to an existing regular non-symlink file: "
            + SqliteBookFilesystemSupport.absolutePath(bookPath));
  }

  private static void requireSecurePosixBookFile(Path bookPath, boolean requiresWrite)
      throws IOException {
    Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(bookPath, LinkOption.NOFOLLOW_LINKS);
    if (!permissions.contains(PosixFilePermission.OWNER_READ)
        || (requiresWrite && !permissions.contains(PosixFilePermission.OWNER_WRITE))
        || !POSIX_BOOK_FILE_PERMISSIONS.containsAll(permissions)) {
      throw ownerOnlyBookFileRequired(bookPath);
    }
  }

  private static void requireSecureAclBookFile(Path bookPath, boolean requiresWrite)
      throws IOException {
    AclFileAttributeView view = SqliteBookAclSupport.aclView(bookPath);
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
      throw ownerOnlyBookFileRequired(bookPath);
    }
  }

  private static SqliteCallerPathContractException ownerOnlyBookFileRequired(Path bookPath) {
    return new SqliteCallerPathContractException(
        bookPath,
        SqliteCallerPathFailure.TARGET_OWNER_ONLY_REQUIRED,
        "The FinGrind SQLite book file must already use owner-only permissions: "
            + SqliteBookFilesystemSupport.absolutePath(bookPath));
  }
}
