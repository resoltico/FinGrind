package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

/** Production platform operations behind the owner-only file capability. */
final class PrivateOutputFilePlatformOperations implements PrivateOutputFile.Operations {
  private static final Set<PosixFilePermission> OWNER_READ_WRITE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private final PosixFileCreator posixFileCreator;
  private final PosixFileOpener posixFileOpener;
  private final WindowsFileCreator windowsFileCreator;
  private final WindowsFileOpener windowsFileOpener;

  /** Binds the production adapter to the exact NIO and Windows native channel primitives. */
  PrivateOutputFilePlatformOperations() {
    this(
        PrivateOutputFilePlatformOperations::createNewPosixChannel,
        PrivateOutputFilePlatformOperations::openExistingPosixChannel,
        WindowsPrivateOutputFilePlatformAdapter.PRODUCTION,
        WindowsPrivateOutputFilePlatformAdapter.PRODUCTION);
  }

  /** Binds exact POSIX and Windows channel-opening primitives to one platform adapter. */
  PrivateOutputFilePlatformOperations(
      PosixFileCreator posixFileCreator,
      PosixFileOpener posixFileOpener,
      WindowsFileCreator windowsFileCreator,
      WindowsFileOpener windowsFileOpener) {
    this.posixFileCreator = Objects.requireNonNull(posixFileCreator, "posixFileCreator");
    this.posixFileOpener = Objects.requireNonNull(posixFileOpener, "posixFileOpener");
    this.windowsFileCreator = Objects.requireNonNull(windowsFileCreator, "windowsFileCreator");
    this.windowsFileOpener = Objects.requireNonNull(windowsFileOpener, "windowsFileOpener");
  }

  @Override
  public boolean supportsPosix(Path file) {
    return file.getFileSystem().supportedFileAttributeViews().contains("posix");
  }

  @Override
  public boolean supportsAcl(Path file) {
    return file.getFileSystem().supportedFileAttributeViews().contains("acl");
  }

  @Override
  public boolean isWindows() {
    return PrivateOutputFile.isWindows(System.getProperty("os.name", ""));
  }

  @Override
  public void requireSecureParent(Path file) throws IOException {
    Path parent = Objects.requireNonNull(file.getParent(), "normalized file parent");
    PrivateOutputDirectory.requireExistingOwnerOnly(parent);
  }

  @Override
  public PrivateOutputFile.OpenedFile createNewPosix(Path file) throws IOException {
    try {
      return PrivateOutputFile.createdPosix(posixFileCreator.createNew(file), file);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new PrivateOutputFile.OwnerOnlyFileViolation(
          file,
          PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED,
          "cannot atomically create an owner-only file on its selected filesystem",
          unsupported);
    }
  }

  @Override
  public PrivateOutputFile.OpenedFile createNewWindows(Path file) throws IOException {
    return windowsFileCreator.createNew(file);
  }

  @Override
  public PrivateOutputFile.OpenedFile openExistingPosix(Path file, PrivateOutputFile.Access access)
      throws IOException {
    requireExistingSecurePosixFile(file, access);
    try {
      return new PrivateOutputFilePosixOpenedFile(
          posixFileOpener.openExisting(file, access), false, file);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new PrivateOutputFile.OwnerOnlyFileViolation(
          file,
          PrivateOutputFile.ViolationKind.ATOMIC_CREATION_UNSUPPORTED,
          "cannot enforce nofollow access on its selected filesystem",
          unsupported);
    }
  }

  @Override
  public PrivateOutputFile.OpenedFile openExistingWindows(
      Path file, PrivateOutputFile.Access access) throws IOException {
    return windowsFileOpener.openExisting(file, access);
  }

  private static void requireExistingSecurePosixFile(Path file, PrivateOutputFile.Access access)
      throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw PrivateOutputFile.regularFileRequired(file);
    }
    Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
    boolean ownerCanRead = permissions.contains(PosixFilePermission.OWNER_READ);
    boolean ownerCanWrite = permissions.contains(PosixFilePermission.OWNER_WRITE);
    if (!ownerCanRead
        || (access == PrivateOutputFile.Access.READ_WRITE && !ownerCanWrite)
        || !OWNER_READ_WRITE.containsAll(permissions)) {
      throw PrivateOutputFile.ownerOnlyRequired(file);
    }
  }

  private static FileChannel createNewPosixChannel(Path file) throws IOException {
    return FileChannel.open(
        file,
        Set.<OpenOption>of(
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW,
            LinkOption.NOFOLLOW_LINKS),
        PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
  }

  private static FileChannel openExistingPosixChannel(Path file, PrivateOutputFile.Access access)
      throws IOException {
    return access == PrivateOutputFile.Access.READ_ONLY
        ? FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
        : FileChannel.open(
            file, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
  }

  /** Creates one exact POSIX owner-only file through the selected NIO channel primitive. */
  @FunctionalInterface
  interface PosixFileCreator {
    /** Creates the supplied file and returns its retained exact channel. */
    FileChannel createNew(Path file) throws IOException;
  }

  /** Opens one exact POSIX owner-only file through the selected NIO channel primitive. */
  @FunctionalInterface
  interface PosixFileOpener {
    /** Opens the supplied file with the requested access and returns its retained exact channel. */
    FileChannel openExisting(Path file, PrivateOutputFile.Access access) throws IOException;
  }

  /** Creates one exact protected Windows owner-only file through the selected native transport. */
  @FunctionalInterface
  interface WindowsFileCreator {
    /** Creates the supplied file and returns its retained protected native handle. */
    PrivateOutputFile.OpenedFile createNew(Path file) throws IOException;
  }

  /** Opens one exact protected Windows owner-only file through the selected native transport. */
  @FunctionalInterface
  interface WindowsFileOpener {
    /** Opens the supplied file with the requested access and returns its retained native handle. */
    PrivateOutputFile.OpenedFile openExisting(Path file, PrivateOutputFile.Access access)
        throws IOException;
  }
}
