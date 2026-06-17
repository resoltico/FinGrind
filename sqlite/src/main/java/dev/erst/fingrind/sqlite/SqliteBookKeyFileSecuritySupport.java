package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Objects;
import java.util.Set;

/** Shared filesystem, redaction, and contract-failure support for book-key security. */
final class SqliteBookKeyFileSecuritySupport {
  static final String POSIX_OWNER_READ_WRITE_DESCRIPTOR = "0600";
  static final String WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR = "owner-only-acl";
  static final String POSIX_VIEW = "posix";
  static final String ACL_VIEW = "acl";

  private SqliteBookKeyFileSecuritySupport() {}

  static String generatedPermissionsDescriptor(Path normalizedPath) {
    if (supportsPosix(normalizedPath)) {
      return POSIX_OWNER_READ_WRITE_DESCRIPTOR;
    }
    if (supportsAcl(normalizedPath)) {
      return WINDOWS_OWNER_ONLY_ACL_DESCRIPTOR;
    }
    throw new IllegalArgumentException(unsupportedSecureFilesystemMessage(normalizedPath));
  }

  static void requireSupportedSecureFilesystem(Path normalizedPath) {
    if (!supportsPosix(normalizedPath) && !supportsAcl(normalizedPath)) {
      throw new SqliteCallerPathContractException(
          normalizedPath,
          SqliteCallerPathFailure.UNSUPPORTED_SECURE_FILESYSTEM,
          unsupportedSecureFilesystemMessage(normalizedPath));
    }
  }

  static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(POSIX_VIEW);
  }

  static boolean supportsAcl(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(ACL_VIEW);
  }

  static AclFileAttributeView aclView(Path path) {
    AclFileAttributeView view =
        Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new UnsupportedOperationException("no ACL file attribute view is available");
    }
    return view;
  }

  static ContractFailure invalidBookKeyFile(String message) {
    return invalidBookKeyFile(message, generalKeyFileHint());
  }

  static ContractFailure invalidBookKeyFile(String message, String hint) {
    return ContractErrors.Descriptor.INVALID_BOOK_KEY_FILE.failure(message, hint, null);
  }

  static String generalKeyFileHint() {
    return "Choose one regular non-symlink key file path beneath one private owner-only parent directory. If the parent directory already exists, tighten it first; otherwise target one missing private directory so FinGrind can create it securely, then rerun the command.";
  }

  static ContractFailure unsupportedSecureFilesystem(Path path, RuntimeException cause) {
    Objects.requireNonNull(cause, "cause");
    return invalidBookKeyFile(unsupportedSecureFilesystemMessage(path));
  }

  static String unsupportedSecureFilesystemMessage(Path path) {
    return "The FinGrind book key file must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs: "
        + redactedPath(path);
  }

  static Path requireKeyFileParentDirectory(Path bookKeyFilePath) {
    Path parentDirectory = bookKeyFilePath.getParent();
    if (parentDirectory == null) {
      throw new SqliteCallerPathContractException(
          bookKeyFilePath,
          SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
          "The FinGrind book key file must resolve beneath a parent directory: "
              + redactedPath(bookKeyFilePath));
    }
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          bookKeyFilePath,
          SqliteCallerPathFailure.PARENT_PATH_COLLISION,
          "The FinGrind book key file must resolve beneath one real parent directory: "
              + redactedPath(bookKeyFilePath));
    }
    return parentDirectory;
  }

  static String redactedPath(Path path) {
    return PublicPathHint.fromPath(path).value();
  }

  static boolean containsAny(
      Set<AclEntryPermission> permissions, Set<AclEntryPermission> requiredPermissions) {
    return requiredPermissions.stream().anyMatch(permissions::contains);
  }
}
