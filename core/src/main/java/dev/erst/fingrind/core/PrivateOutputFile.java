package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Exact-channel admission for owner-only artifacts written beneath a private output directory.
 *
 * <p>A new artifact is never created with inherited access and repaired afterwards. POSIX uses
 * {@code CREATE_NEW}, {@code NOFOLLOW_LINKS}, and {@code 0600} in one open operation. Windows uses
 * a native protected owner-only descriptor on the {@code CreateFileW} call and retains that exact
 * handle for every write, force, and close operation.
 */
public final class PrivateOutputFile {
  private static final Operations PRODUCTION_OPERATIONS = new PrivateOutputFilePlatformOperations();

  private PrivateOutputFile() {}

  /** Creates one fresh, empty owner-only regular file and retains its exact creation channel. */
  public static OpenedFile createNew(Path file) throws IOException {
    return PrivateOutputFileAdmission.createNew(file, PRODUCTION_OPERATIONS);
  }

  /** Opens and admits an existing owner-only regular file without changing its permissions. */
  public static OpenedFile openExisting(Path file, Access access) throws IOException {
    return PrivateOutputFileAdmission.openExisting(file, access, PRODUCTION_OPERATIONS);
  }

  /** Opens an owner-only file, creating it exactly once when its pathname remains absent. */
  public static OpenedFile openOrCreate(Path file) throws IOException {
    try {
      return createNew(file);
    } catch (FileAlreadyExistsException collision) {
      return openExisting(file, Access.READ_WRITE);
    }
  }

  /** Returns the explicit physical filesystem identity for one admitted owner-only artifact. */
  public static String physicalObjectIdentity(Path file) throws IOException {
    try (OpenedFile opened = openExisting(file, Access.READ_ONLY)) {
      return opened.physicalObjectIdentity();
    }
  }

  /** Validates an existing owner-only regular file without retaining an open channel. */
  public static void requireExistingOwnerOnly(Path file, Access access) throws IOException {
    PrivateOutputFileAdmission.requireExistingOwnerOnly(file, access, PRODUCTION_OPERATIONS);
  }

  static boolean isWindows(String operatingSystemName) {
    return Objects.requireNonNull(operatingSystemName, "operatingSystemName")
        .toLowerCase(Locale.ROOT)
        .contains("windows");
  }

  static void requireLockRange(long position, long size) {
    if (position < 0L || size <= 0L || position > Long.MAX_VALUE - size) {
      throw new IllegalArgumentException(
          "position must be non-negative, size must be positive, and the range must not overflow.");
    }
  }

  static OpenedFile wrap(FileChannel channel) {
    return new PrivateOutputFilePosixOpenedFile(
        Objects.requireNonNull(channel, "channel"), false, null);
  }

  static OpenedFile createdPosix(FileChannel channel, Path file) {
    return new PrivateOutputFilePosixOpenedFile(
        Objects.requireNonNull(channel, "channel"), true, file);
  }

  static OwnerOnlyFileViolation regularFileRequired(Path file) {
    return new OwnerOnlyFileViolation(
        file, ViolationKind.REGULAR_NON_SYMLINK_REQUIRED, "must be one regular non-symlink file");
  }

  static OwnerOnlyFileViolation ownerOnlyRequired(Path file) {
    return new OwnerOnlyFileViolation(
        file, ViolationKind.OWNER_ONLY_REQUIRED, "must retain owner-only file access");
  }

  /** The access requested while admitting one existing owner-only file. */
  public enum Access {
    READ_ONLY,
    READ_WRITE
  }

  /** Stable categories for a private-file admission failure. */
  public enum ViolationKind {
    MISSING_PARENT,
    PARENT_OWNER_ONLY_REQUIRED,
    REGULAR_NON_SYMLINK_REQUIRED,
    OWNER_ONLY_REQUIRED,
    ATOMIC_CREATION_UNSUPPORTED
  }

  /** A deterministic owner-only file admission failure. */
  public static final class OwnerOnlyFileViolation extends IOException {
    private static final long serialVersionUID = 1L;

    private final Path file;
    private final ViolationKind kind;

    OwnerOnlyFileViolation(Path file, ViolationKind kind, String requirement) {
      this(file, kind, requirement, null);
    }

    OwnerOnlyFileViolation(
        Path file, ViolationKind kind, String requirement, @Nullable Throwable cause) {
      super(
          "Private output file "
              + Objects.requireNonNull(file, "file")
              + " "
              + Objects.requireNonNull(requirement, "requirement")
              + ".",
          cause);
      this.file = file;
      this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** Returns the normalized artifact path that failed admission. */
    public Path file() {
      return file;
    }

    /** Returns the closed failure category. */
    public ViolationKind kind() {
      return kind;
    }
  }

  /**
   * One retained exact owner-only file channel.
   *
   * <p>Callers must close the object after force-confirming their content. On Windows this is the
   * native handle created with the protected descriptor, not a pathname reopened through NIO.
   */
  public interface OpenedFile extends ReadableByteChannel, WritableByteChannel {
    /** Returns whether this exact channel atomically created its pathname. */
    boolean created();

    /** Reads through the retained exact channel. */
    @Override
    int read(ByteBuffer destination) throws IOException;

    /** Writes through the retained exact channel. */
    @Override
    int write(ByteBuffer source) throws IOException;

    /** Reports whether this retained exact channel remains open. */
    @Override
    boolean isOpen();

    /** Returns the current byte length through the retained exact channel. */
    long size() throws IOException;

    /** Truncates through the retained exact channel. */
    void truncate(long size) throws IOException;

    /** Moves this retained channel's logical cursor to one exact non-negative byte position. */
    void position(long position) throws IOException;

    /** Force-confirms file content and metadata through the retained exact channel. */
    void force() throws IOException;

    /** Attempts one exclusive byte-range lock through this retained exact channel. */
    @Nullable HeldLock tryExclusiveLock(long position, long size) throws IOException;

    /** Returns the explicit physical identity represented by this retained exact channel. */
    String physicalObjectIdentity() throws IOException;

    /** Closes the retained exact channel. */
    @Override
    void close() throws IOException;
  }

  /** Releases one exact byte-range lock while its owner retains the opened file channel. */
  @FunctionalInterface
  public interface HeldLock extends AutoCloseable {
    /** Releases the exact byte-range lock. */
    @Override
    void close() throws IOException;
  }

  /** Injectable platform facts and exact-channel operations for deterministic tests. */
  interface Operations {
    /** Reports whether the supplied file belongs to a POSIX-capable filesystem. */
    boolean supportsPosix(Path file);

    /** Reports whether the supplied file belongs to an ACL-capable filesystem. */
    boolean supportsAcl(Path file);

    /** Reports whether the active platform is Windows. */
    boolean isWindows();

    /** Requires the file's parent directory to satisfy the private-output admission contract. */
    void requireSecureParent(Path file) throws IOException;

    /** Atomically creates a POSIX owner-only file and returns its exact channel. */
    OpenedFile createNewPosix(Path file) throws IOException;

    /** Atomically creates a Windows owner-only file and returns its exact native handle. */
    OpenedFile createNewWindows(Path file) throws IOException;

    /** Admits an existing POSIX owner-only file through a no-follow exact channel. */
    OpenedFile openExistingPosix(Path file, Access access) throws IOException;

    /** Admits an existing Windows owner-only file through its retained native handle. */
    OpenedFile openExistingWindows(Path file, Access access) throws IOException;
  }
}
