package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.nio.file.Path;
import java.util.Objects;

/** Shared filesystem and redaction support for protected SQLite book artifacts. */
final class SqliteBookFilesystemSupport {
  private static final String POSIX_VIEW = "posix";
  private static final String ACL_VIEW = "acl";

  private SqliteBookFilesystemSupport() {}

  static void requireSupportedSecureFilesystem(Path path) {
    if (!supportsPosix(path) && !supportsAcl(path)) {
      throw new IllegalStateException(unsupportedSecureFilesystemMessage(path));
    }
  }

  static boolean supportsPosix(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(POSIX_VIEW);
  }

  static boolean supportsAcl(Path path) {
    return path.getFileSystem().supportedFileAttributeViews().contains(ACL_VIEW);
  }

  static Path requireBookParentDirectory(Path normalizedBookPath) {
    Path parentDirectory =
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath").getParent();
    if (parentDirectory == null) {
      throw new IllegalArgumentException(
          "The FinGrind SQLite book path must resolve to a file beneath a parent directory: "
              + redactedPath(normalizedBookPath));
    }
    return parentDirectory;
  }

  static String unsupportedSecureFilesystemMessage(Path path) {
    return "The FinGrind SQLite book file must live on a filesystem that supports POSIX owner-only permissions or Windows owner-only ACLs: "
        + redactedPath(path);
  }

  static String redactedPath(Path path) {
    return PublicPathHint.fromPath(path).value();
  }
}
