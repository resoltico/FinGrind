package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Public owner for opt-in tightening of existing SQLite artifact parent directories. */
public final class SqliteCallerPathSecurity {
  private SqliteCallerPathSecurity() {}

  /**
   * Tightens an existing book-file parent directory when it is owner-accessible but not yet
   * owner-only.
   */
  public static Optional<Path> tightenExistingBookParentDirectory(Path bookFilePath)
      throws IOException {
    Path normalizedBookPath = normalize(bookFilePath);
    Path parentDirectory = normalizedBookPath.getParent();
    if (parentDirectory == null || !isExistingDirectory(parentDirectory)) {
      return Optional.empty();
    }
    if (!supportsSecureFilesystem(parentDirectory)) {
      return Optional.empty();
    }
    try {
      SqliteBookDirectorySecurity.requireSecureExistingDirectory(
          normalizedBookPath, parentDirectory);
      return Optional.empty();
    } catch (SqliteCallerPathContractException exception) {
      if (exception.pathFailure() != SqliteCallerPathFailure.PARENT_OWNER_ONLY_REQUIRED) {
        return Optional.empty();
      }
      SqliteBookDirectorySecurity.hardenDirectory(parentDirectory);
      return Optional.of(parentDirectory);
    }
  }

  /**
   * Tightens an existing key-file parent directory when it is owner-accessible but not yet
   * owner-only.
   */
  public static Optional<Path> tightenExistingBookKeyParentDirectory(Path bookKeyFilePath)
      throws IOException {
    Path normalizedBookKeyPath = normalize(bookKeyFilePath);
    Path parentDirectory = normalizedBookKeyPath.getParent();
    if (parentDirectory == null || !isExistingDirectory(parentDirectory)) {
      return Optional.empty();
    }
    if (!supportsSecureFilesystem(parentDirectory)) {
      return Optional.empty();
    }
    if (SqliteBookKeyFileDirectorySecurity.hardenExistingOwnerAccessibleDirectory(
        parentDirectory)) {
      return Optional.of(parentDirectory);
    }
    return Optional.empty();
  }

  private static Path normalize(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }

  private static boolean isExistingDirectory(Path directoryPath) {
    return Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)
        && Files.isDirectory(directoryPath, LinkOption.NOFOLLOW_LINKS);
  }

  private static boolean supportsSecureFilesystem(Path path) {
    return SqliteBookFilesystemSupport.supportsPosix(path)
        || SqliteBookFilesystemSupport.supportsAcl(path);
  }
}
