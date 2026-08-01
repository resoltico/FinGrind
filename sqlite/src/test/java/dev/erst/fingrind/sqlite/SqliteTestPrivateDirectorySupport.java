package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
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

/** Shared helpers for allocating owner-only temporary directories in SQLite tests. */
public final class SqliteTestPrivateDirectorySupport {
  private SqliteTestPrivateDirectorySupport() {}

  public static void hardenOwnerOnlyDirectory(Path directoryPath) {
    if (!Files.isDirectory(directoryPath)) {
      return;
    }
    try {
      hardenExistingOwnerOnlyDirectory(directoryPath);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to harden one SQLite test temporary directory: " + directoryPath, exception);
    }
  }

  private static void hardenExistingOwnerOnlyDirectory(Path directoryPath) throws IOException {
    if (directoryPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(
          directoryPath,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
      return;
    }
    AclFileAttributeView view =
        Files.getFileAttributeView(
            directoryPath, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
    if (view == null) {
      throw new IOException("Test filesystem has no owner-only directory security view.");
    }
    UserPrincipal owner = view.getOwner();
    view.setAcl(
        List.of(
            AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                    AclEntryPermission.LIST_DIRECTORY,
                    AclEntryPermission.ADD_FILE,
                    AclEntryPermission.ADD_SUBDIRECTORY,
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
                    AclEntryPermission.SYNCHRONIZE)
                .build()));
  }

  /** Resolves a test-created directory to its physical spelling before production admission. */
  public static Path canonicalExistingDirectory(Path directoryPath) {
    try {
      return Objects.requireNonNull(directoryPath, "directoryPath").toRealPath();
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to canonicalize one SQLite test temporary directory: " + directoryPath,
          exception);
    }
  }

  /** Canonicalizes and hardens one test-created parent before it is supplied as a path input. */
  public static Path canonicalizeAndHardenOwnerOnlyDirectory(Path directoryPath) {
    Path canonicalDirectory = canonicalExistingDirectory(directoryPath);
    hardenOwnerOnlyDirectory(canonicalDirectory);
    return canonicalDirectory;
  }

  public static Path createOwnerOnlyTempDirectory(String prefix) {
    try {
      Path directoryPath;
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        directoryPath =
            Files.createTempDirectory(
                prefix,
                PosixFilePermissions.asFileAttribute(
                    Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE)));
      } else {
        directoryPath = Files.createTempDirectory(prefix);
      }
      Path canonicalDirectory = canonicalizeAndHardenOwnerOnlyDirectory(directoryPath);
      return canonicalDirectory;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to create one owner-only SQLite test temporary directory for prefix "
              + prefix
              + ".",
          exception);
    }
  }

  /**
   * Writes one UTF-8 fixture through the same exact owner-only creation capability as production.
   */
  public static Path writeOwnerOnlyUtf8File(Path filePath, String content) throws IOException {
    Path checkedFilePath = Objects.requireNonNull(filePath, "filePath");
    ByteBuffer bytes =
        java.nio.charset.StandardCharsets.UTF_8.encode(Objects.requireNonNull(content, "content"));
    try (PrivateOutputFile.OpenedFile file = PrivateOutputFile.createNew(checkedFilePath)) {
      while (bytes.hasRemaining()) {
        if (file.write(bytes) <= 0) {
          throw new IOException("Could not write one owner-only SQLite test fixture.");
        }
      }
      file.force();
    }
    return checkedFilePath;
  }
}
