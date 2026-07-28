package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Retained owner-only coordination control files and their platform-native locking transport.
 *
 * <p>Every protocol keeps its immutable magic in the first {@value #CONTROL_LOCK_BASE} bytes.
 * Activity and maintenance locks start strictly after that header so a Windows byte-range lock
 * never prevents another process from reading and validating the control identity that it needs to
 * coordinate with the holder. The returned handle is deliberately opaque: callers can only retain
 * or release the exact operating-system object that acquired the lock.
 */
final class SqliteCoordinationControlFiles {
  static final long CONTROL_LOCK_BASE = 4_096L;

  private SqliteCoordinationControlFiles() {}

  /** Returns the first byte available to the slot numbered by this protocol. */
  static long activitySlotPosition(int slot) {
    if (slot < 0) {
      throw new IllegalArgumentException("Coordination activity slot must not be negative.");
    }
    return Math.addExact(CONTROL_LOCK_BASE, slot);
  }

  /** Returns the first byte of the sole full-control maintenance exclusion. */
  static long maintenanceLockPosition() {
    return CONTROL_LOCK_BASE;
  }

  /** Returns the length of the full-control exclusion without crossing the signed long boundary. */
  static long maintenanceLockLength() {
    return Long.MAX_VALUE - CONTROL_LOCK_BASE;
  }

  /**
   * Opens or creates one exact control file and attempts its exclusive protocol lock.
   *
   * <p>On Windows this keeps the native nofollow {@code HANDLE} that performed secure creation,
   * header validation, and locking. POSIX keeps the equivalent nofollow {@link FileChannel}.
   */
  static @Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    requireProtocolLockRange(position, size);
    return currentTransport()
        .openOrCreateAndTryExclusiveLock(controlPath, checkedMagic(magic), position, size);
  }

  /** Opens one existing exact control file and attempts its exclusive protocol lock. */
  static @Nullable LockedControlFile openExistingAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    requireProtocolLockRange(position, size);
    return currentTransport()
        .openExistingAndTryExclusiveLock(controlPath, checkedMagic(magic), position, size);
  }

  /** Creates one immutable owner-only record through the platform's exact creation boundary. */
  static void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
    Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
    byte[] checkedMagic = checkedMagic(magic);
    currentTransport().createAtomicallySecureRecord(checkedPath, checkedMagic);
  }

  /** Validates one existing owner-only immutable record without exposing its transport. */
  static void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
    Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
    byte[] checkedMagic = checkedMagic(magic);
    currentTransport().requireExistingExactRecord(checkedPath, checkedMagic);
  }

  /**
   * Opens one brand-new FinGrind-owned general protocol file on its exact POSIX creation channel.
   *
   * <p>This is the broader stage/evidence boundary, whose {@link FileChannel} contract predates the
   * opaque coordination transport. Windows coordination files use the direct-handle FFM transport
   * above; generic stage creation continues to fail closed where NIO cannot create an owner-only
   * ACL atomically.
   */
  static java.nio.channels.FileChannel openNewOwnerOnlyProtocolFile(Path protocolPath)
      throws IOException {
    return SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(protocolPath);
  }

  /** Returns a stable bounded hash for a caller-owned identity description. */
  static String sha256Hex(String identity) {
    return CryptographicPrimitives.sha256HexUtf8(Objects.requireNonNull(identity, "identity"));
  }

  /** Produces immutable bounded header text binding one protocol version to one identity. */
  static byte[] magic(String protocol, String binding) {
    return (Objects.requireNonNull(protocol, "protocol")
            + ":"
            + Objects.requireNonNull(binding, "binding")
            + "\n")
        .getBytes(StandardCharsets.US_ASCII);
  }

  /**
   * Returns the explicit O(1) filesystem identity for one existing regular physical object.
   *
   * <p>POSIX identities are the provider's {@code unix:dev}/{@code unix:ino} tuple. Windows
   * identities are retrieved from the exact nofollow native handle. Other providers are rejected:
   * an opaque {@code fileKey().toString()} is not a coordination protocol.
   */
  static String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
    Path checkedPath = Objects.requireNonNull(existingArtifactPath, "existingArtifactPath");
    return currentTransport().physicalObjectIdentity(checkedPath);
  }

  /** Returns the stable binding for a canonical real directory path. */
  static String canonicalDirectoryBinding(Path canonicalDirectory) {
    Path checkedDirectory = Objects.requireNonNull(canonicalDirectory, "canonicalDirectory");
    return sha256Hex(
        "FinGrind-maintenance-directory-v4\u0000" + checkedDirectory.toAbsolutePath().normalize());
  }

  static boolean isWindows() {
    return "windows"
        .equals(
            SqliteHostPlatformDescriptor.supportedOperatingSystemId(
                System.getProperty("os.name", "")));
  }

  static CoordinationTransport transportFor(boolean windows) {
    return windows ? CoordinationTransport.WINDOWS : CoordinationTransport.POSIX;
  }

  private static CoordinationTransport currentTransport() {
    return transportFor(isWindows());
  }

  static LockedControlFile lockedControlFile(Path controlPath, CloseOperation closeOperation) {
    return new LockedControlFile(controlPath, closeOperation);
  }

  private static void requireProtocolLockRange(long position, long size) {
    if (position < CONTROL_LOCK_BASE || size <= 0L || size > Long.MAX_VALUE - position) {
      throw new IllegalArgumentException(
          "FinGrind coordination locks must remain wholly after the immutable control header.");
    }
  }

  private static byte[] checkedMagic(byte[] magic) {
    byte[] checkedMagic = Objects.requireNonNull(magic, "magic").clone();
    if (checkedMagic.length == 0 || checkedMagic.length >= CONTROL_LOCK_BASE) {
      throw new IllegalArgumentException(
          "Coordination control-file magic must fit wholly inside the immutable header.");
    }
    return checkedMagic;
  }

  /** Platform-native operations for one retained coordination protocol file. */
  enum CoordinationTransport {
    WINDOWS {
      @Override
      @Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
          Path controlPath, byte[] magic, long position, long size) throws IOException {
        return SqliteWindowsCoordinationFfmTransport.openOrCreateAndTryExclusiveLock(
            controlPath, magic, position, size);
      }

      @Override
      @Nullable LockedControlFile openExistingAndTryExclusiveLock(
          Path controlPath, byte[] magic, long position, long size) throws IOException {
        return SqliteWindowsCoordinationFfmTransport.openExistingAndTryExclusiveLock(
            controlPath, magic, position, size);
      }

      @Override
      void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
        SqliteWindowsCoordinationFfmTransport.createAtomicallySecureRecord(recordPath, magic);
      }

      @Override
      void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
        SqliteWindowsCoordinationFfmTransport.requireExistingExactRecord(recordPath, magic);
      }

      @Override
      String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
        return SqliteWindowsCoordinationFfmTransport.physicalObjectIdentity(existingArtifactPath);
      }
    },
    POSIX {
      @Override
      @Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
          Path controlPath, byte[] magic, long position, long size) throws IOException {
        return SqlitePosixCoordinationControlFileTransport.openOrCreateAndTryExclusiveLock(
            controlPath, magic, position, size);
      }

      @Override
      @Nullable LockedControlFile openExistingAndTryExclusiveLock(
          Path controlPath, byte[] magic, long position, long size) throws IOException {
        return SqlitePosixCoordinationControlFileTransport.openExistingAndTryExclusiveLock(
            controlPath, magic, position, size);
      }

      @Override
      void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
        SqlitePosixCoordinationControlFileTransport.createAtomicallySecureRecord(recordPath, magic);
      }

      @Override
      void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
        SqlitePosixCoordinationControlFileTransport.requireExistingExactRecord(recordPath, magic);
      }

      @Override
      String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
        return SqlitePosixCoordinationFileSecurity.physicalObjectIdentity(existingArtifactPath);
      }
    };

    abstract @Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
        Path controlPath, byte[] magic, long position, long size) throws IOException;

    abstract @Nullable LockedControlFile openExistingAndTryExclusiveLock(
        Path controlPath, byte[] magic, long position, long size) throws IOException;

    abstract void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException;

    abstract void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException;

    abstract String physicalObjectIdentity(Path existingArtifactPath) throws IOException;
  }

  /** Closes one opaque retained control resource through its owning transport. */
  @FunctionalInterface
  interface CloseOperation {
    /** Releases the exact native lock and descriptor retained for one coordination control file. */
    void close() throws IOException;
  }

  /** One opaque retained control-file lock, released and closed together exactly once. */
  static final class LockedControlFile implements AutoCloseable {
    private final Path controlPath;
    private final CloseOperation closeOperation;
    private boolean closed;

    private LockedControlFile(Path controlPath, CloseOperation closeOperation) {
      this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
      this.closeOperation = Objects.requireNonNull(closeOperation, "closeOperation");
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      try {
        closeOperation.close();
      } catch (IOException failure) {
        throw new IOException(
            "Failed to release the FinGrind coordination control-file lock at " + controlPath + ".",
            failure);
      }
    }
  }
}
