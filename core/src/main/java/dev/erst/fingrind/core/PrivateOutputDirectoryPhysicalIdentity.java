package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves stable no-follow physical identities for private directories that coordinate leases. */
final class PrivateOutputDirectoryPhysicalIdentity {
  private PrivateOutputDirectoryPhysicalIdentity() {}

  static String physicalObjectIdentity(Path directory) throws PrivateOutputDirectory.Violation {
    return physicalObjectIdentity(directory, new ProductionOperations());
  }

  static String physicalObjectIdentity(Path directory, Operations physicalIdentityOperations)
      throws PrivateOutputDirectory.Violation {
    Path checkedDirectory =
        Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    Operations checkedOperations =
        Objects.requireNonNull(physicalIdentityOperations, "physicalIdentityOperations");
    checkedOperations.requireExistingOwnerOnly(checkedDirectory);
    if (checkedOperations.supportsPosix(checkedDirectory)) {
      return readPhysicalIdentity(checkedDirectory, checkedOperations::posixPhysicalIdentity);
    }
    if (checkedOperations.supportsAcl(checkedDirectory) && checkedOperations.isWindows()) {
      return readPhysicalIdentity(checkedDirectory, checkedOperations::windowsPhysicalIdentity);
    }
    throw failure(
        checkedDirectory,
        new UnsupportedOperationException(
            "The private-output filesystem did not expose a stable physical directory identity."));
  }

  private static String readPhysicalIdentity(Path directory, IdentityReader reader)
      throws PrivateOutputDirectory.Violation {
    try {
      return reader.read(directory);
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw failure(directory, exception);
    }
  }

  private static PrivateOutputDirectory.Violation failure(Path directory, Throwable cause) {
    return new PrivateOutputDirectory.Violation(
        "FinGrind could not establish the physical identity of private output directory "
            + PrivateOutputDirectoryFailures.absolutePath(directory)
            + ".",
        cause);
  }

  /** Injectable identity facts used to prove platform-neutral publication lease ordering. */
  interface Operations {
    /** Requires the directory to retain the private-output admission contract. */
    void requireExistingOwnerOnly(Path directory) throws PrivateOutputDirectory.Violation;

    /** Reports whether the directory's filesystem exposes POSIX device/inode identity. */
    boolean supportsPosix(Path directory);

    /** Reports whether the directory's filesystem exposes Windows ACL identity. */
    boolean supportsAcl(Path directory);

    /** Reports whether the active platform is Windows. */
    boolean isWindows();

    /** Returns the no-follow POSIX physical directory identity. */
    String posixPhysicalIdentity(Path directory) throws IOException;

    /** Returns the exact native Windows physical directory identity. */
    String windowsPhysicalIdentity(Path directory) throws IOException;
  }

  /** Production platform facts and native handle access for physical directory identity. */
  static final class ProductionOperations implements Operations {
    private final PrivateOutputDirectory.FilesystemAccess filesystemAccess;
    private final String operatingSystemName;
    private final WindowsPhysicalIdentityReader windowsIdentityReader;

    ProductionOperations() {
      this(
          PrivateOutputDirectory.filesystemAccess(),
          System.getProperty("os.name", ""),
          WindowsPrivateOutputFilePlatformAdapter.PRODUCTION::physicalDirectoryIdentity);
    }

    ProductionOperations(
        PrivateOutputDirectory.FilesystemAccess filesystemAccess,
        String operatingSystemName,
        WindowsPhysicalIdentityReader windowsIdentityReader) {
      this.filesystemAccess = Objects.requireNonNull(filesystemAccess, "filesystemAccess");
      this.operatingSystemName = Objects.requireNonNull(operatingSystemName, "operatingSystemName");
      this.windowsIdentityReader =
          Objects.requireNonNull(windowsIdentityReader, "windowsIdentityReader");
    }

    @Override
    public void requireExistingOwnerOnly(Path directory) throws PrivateOutputDirectory.Violation {
      PrivateOutputDirectory.requireExistingOwnerOnly(directory);
    }

    @Override
    public boolean supportsPosix(Path directory) {
      return filesystemAccess.supportsPosix(directory);
    }

    @Override
    public boolean supportsAcl(Path directory) {
      return filesystemAccess.supportsAcl(directory);
    }

    @Override
    public boolean isWindows() {
      return PrivateOutputFile.isWindows(operatingSystemName);
    }

    @Override
    public String posixPhysicalIdentity(Path directory) throws IOException {
      return PrivateOutputFilePosixOpenedFile.physicalObjectIdentity(
          Files.readAttributes(directory, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS));
    }

    @Override
    public String windowsPhysicalIdentity(Path directory) throws IOException {
      return windowsIdentityReader.read(directory);
    }
  }

  /** Reads one platform-native directory identity through a no-follow proof. */
  @FunctionalInterface
  private interface IdentityReader {
    /** Returns one physical directory identity. */
    String read(Path directory) throws IOException;
  }

  /** Reads one exact Windows directory identity through a protected native handle. */
  @FunctionalInterface
  interface WindowsPhysicalIdentityReader {
    /** Returns the native physical directory identity. */
    String read(Path directory) throws IOException;
  }
}
