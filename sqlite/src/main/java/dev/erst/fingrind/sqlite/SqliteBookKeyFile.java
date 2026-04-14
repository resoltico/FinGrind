package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

/** Reads one UTF-8 book passphrase file. */
public final class SqliteBookKeyFile {
  private SqliteBookKeyFile() {}

  /** Reads and normalizes one UTF-8 passphrase file. */
  public static SqliteBookPassphrase load(Path bookKeyFilePath) {
    Path normalizedPath = normalize(bookKeyFilePath);
    requireSecureKeyFile(normalizedPath);
    return SqliteBookPassphrase.fromUtf8Bytes(normalizedPath.toString(), readBytes(normalizedPath));
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static byte[] readBytes(Path bookKeyFilePath) {
    try {
      return Files.readAllBytes(bookKeyFilePath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the FinGrind book key file: " + bookKeyFilePath, exception);
    }
  }

  static void requireSecureKeyFile(Path bookKeyFilePath) {
    requireSecureKeyFile(
        bookKeyFilePath, path -> Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS));
  }

  static void requireSecureKeyFile(Path bookKeyFilePath, PosixPermissionsReader permissionsReader) {
    try {
      if (Files.notExists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      if (!Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        throw new IllegalStateException(
            "The FinGrind book key file must be a regular non-symlink file: " + bookKeyFilePath);
      }
      Set<PosixFilePermission> permissions = permissionsReader.read(bookKeyFilePath);
      if (!permissions.contains(PosixFilePermission.OWNER_READ)) {
        throw new IllegalStateException(
            "The FinGrind book key file must be owner-readable: " + bookKeyFilePath);
      }
      if (!Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
          .containsAll(permissions)) {
        throw new IllegalStateException(
            "The FinGrind book key file must use owner-only permissions (0400 or 0600): "
                + bookKeyFilePath);
      }
    } catch (UnsupportedOperationException exception) {
      throw new IllegalStateException(
          "The FinGrind book key file must live on a POSIX filesystem so owner-only permissions can be enforced: "
              + bookKeyFilePath,
          exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect the FinGrind book key file permissions: " + bookKeyFilePath,
          exception);
    }
  }

  @SuppressWarnings("PMD.CommentRequired")
  @FunctionalInterface
  interface PosixPermissionsReader {
    @SuppressWarnings("PMD.CommentRequired")
    Set<PosixFilePermission> read(Path path) throws IOException;
  }
}
