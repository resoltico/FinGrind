package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** Retained exact owner-only coordination controls and their byte-range locks. */
final class SqliteCoordinationControlFiles {
  private SqliteCoordinationControlFiles() {}

  /** Opens or creates one exact control file and attempts its exclusive protocol lock. */
  static @Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    SqliteCoordinationControlProtocol.requireLockRange(position, size);
    Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
    byte[] checkedMagic = SqliteCoordinationControlProtocol.checkedMagic(magic);
    PrivateOutputFile.OpenedFile opened = openOrCreateOwned(checkedPath);
    try {
      if (opened.created()) {
        initializeNewControl(opened, checkedMagic);
      } else {
        requireExactMagic(opened, checkedMagic);
      }
    } catch (IOException | RuntimeException | Error failure) {
      closePreserving(opened, failure);
      throw failure;
    }
    return tryLockAndRetain(checkedPath, opened, position, size);
  }

  /** Opens one existing exact control file and attempts its exclusive protocol lock. */
  static @Nullable LockedControlFile openExistingAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    SqliteCoordinationControlProtocol.requireLockRange(position, size);
    Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
    byte[] checkedMagic = SqliteCoordinationControlProtocol.checkedMagic(magic);
    PrivateOutputFile.OpenedFile opened =
        openExistingOwned(checkedPath, PrivateOutputFile.Access.READ_WRITE);
    try {
      requireExactMagic(opened, checkedMagic);
    } catch (IOException | RuntimeException | Error failure) {
      closePreserving(opened, failure);
      throw failure;
    }
    return tryLockAndRetain(checkedPath, opened, position, size);
  }

  /** Creates one immutable owner-only record through the core exact-creation capability. */
  static void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
    Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
    byte[] checkedMagic = SqliteCoordinationControlProtocol.checkedMagic(magic);
    try (PrivateOutputFile.OpenedFile opened = createNewOwned(checkedPath)) {
      writeExact(opened, checkedMagic);
    }
  }

  /** Validates one existing immutable owner-only record without retaining a channel. */
  static void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
    Path checkedPath = Objects.requireNonNull(recordPath, "recordPath");
    byte[] checkedMagic = SqliteCoordinationControlProtocol.checkedMagic(magic);
    try (PrivateOutputFile.OpenedFile opened =
        openExistingOwned(checkedPath, PrivateOutputFile.Access.READ_ONLY)) {
      requireExactMagic(opened, checkedMagic);
    }
  }

  /** Returns the explicit identity of one admitted owner-only regular object. */
  static String physicalObjectIdentity(Path existingArtifactPath) throws IOException {
    Path checkedPath = Objects.requireNonNull(existingArtifactPath, "existingArtifactPath");
    try {
      return PrivateOutputFile.physicalObjectIdentity(checkedPath);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(checkedPath, violation);
    }
  }

  private static PrivateOutputFile.OpenedFile openOrCreateOwned(Path path) throws IOException {
    try {
      return PrivateOutputFile.openOrCreate(path);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(path, violation);
    }
  }

  private static PrivateOutputFile.OpenedFile openExistingOwned(
      Path path, PrivateOutputFile.Access access) throws IOException {
    try {
      return PrivateOutputFile.openExisting(path, access);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(path, violation);
    }
  }

  private static PrivateOutputFile.OpenedFile createNewOwned(Path path) throws IOException {
    try {
      return PrivateOutputFile.createNew(path);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(path, violation);
    }
  }

  /**
   * Returns a test seam that delegates exact retained-control cleanup to the supplied operation.
   */
  static LockedControlFile lockedControlFile(Path controlPath, CloseOperation closeOperation) {
    return new LockedControlFile(controlPath, closeOperation);
  }

  /** Returns one retained control whose close releases its exact lock before its file. */
  static LockedControlFile lockedControlFile(
      Path controlPath, PrivateOutputFile.HeldLock lock, PrivateOutputFile.OpenedFile opened) {
    return new LockedControlFile(controlPath, lock, opened);
  }

  static @Nullable LockedControlFile tryLockAndRetain(
      Path controlPath, PrivateOutputFile.OpenedFile opened, long position, long size)
      throws IOException {
    try {
      PrivateOutputFile.@Nullable HeldLock lock = opened.tryExclusiveLock(position, size);
      if (lock == null) {
        opened.close();
        return null;
      }
      return lockedControlFile(controlPath, lock, opened);
    } catch (IOException | RuntimeException | Error failure) {
      closePreserving(opened, failure);
      throw failure;
    }
  }

  private static void initializeNewControl(PrivateOutputFile.OpenedFile opened, byte[] magic)
      throws IOException {
    writeExact(opened, magic);
  }

  static void writeExact(PrivateOutputFile.OpenedFile opened, byte[] magic) throws IOException {
    ByteBuffer bytes = ByteBuffer.wrap(magic);
    while (bytes.hasRemaining()) {
      if (opened.write(bytes) <= 0) {
        throw new IOException(
            "Failed to write the complete FinGrind coordination control-file magic.");
      }
    }
    opened.force();
    requireExactMagic(opened, magic);
  }

  static void requireExactMagic(PrivateOutputFile.OpenedFile opened, byte[] magic)
      throws IOException {
    if (opened.size() != magic.length) {
      throw new IOException("FinGrind coordination control-file magic has an unexpected size.");
    }
    opened.position(0L);
    ByteBuffer actual = ByteBuffer.allocate(magic.length);
    while (actual.hasRemaining()) {
      int read = opened.read(actual);
      if (read < 0) {
        throw new IOException("FinGrind coordination control-file magic ended unexpectedly.");
      }
      if (read == 0) {
        throw new IOException(
            "FinGrind coordination control-file magic did not make read progress.");
      }
    }
    if (!Arrays.equals(actual.array(), magic)) {
      throw new IOException("FinGrind coordination control-file magic is invalid.");
    }
  }

  static void releaseLockAndFile(
      PrivateOutputFile.HeldLock lock, PrivateOutputFile.OpenedFile opened) throws IOException {
    IOException failure = null;
    try {
      lock.close();
    } catch (IOException exception) {
      failure = exception;
    }
    try {
      opened.close();
    } catch (IOException exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  static void closePreserving(PrivateOutputFile.OpenedFile opened, Throwable failure) {
    try {
      opened.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  /** Closes one opaque retained control resource through its owning exact-file capability. */
  @FunctionalInterface
  interface CloseOperation {
    /** Releases the controlled resource. */
    void close() throws IOException;
  }

  /** One opaque retained control-file lock, released and closed together exactly once. */
  static final class LockedControlFile implements AutoCloseable {
    private final Path controlPath;
    private final @Nullable CloseOperation closeOperation;
    private final PrivateOutputFile.@Nullable HeldLock lock;
    private final PrivateOutputFile.@Nullable OpenedFile opened;
    private final ReentrantLock closeLock = new ReentrantLock();
    private boolean closed;

    private LockedControlFile(Path controlPath, CloseOperation closeOperation) {
      this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
      this.closeOperation = Objects.requireNonNull(closeOperation, "closeOperation");
      lock = null;
      opened = null;
    }

    private LockedControlFile(
        Path controlPath, PrivateOutputFile.HeldLock lock, PrivateOutputFile.OpenedFile opened) {
      this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
      closeOperation = null;
      this.lock = Objects.requireNonNull(lock, "lock");
      this.opened = Objects.requireNonNull(opened, "opened");
    }

    /** Releases the retained control file exactly once. */
    @Override
    public void close() throws IOException {
      closeLock.lock();
      try {
        if (closed) {
          return;
        }
        closed = true;
        try {
          releaseRetainedControl();
        } catch (IOException failure) {
          throw new IOException(
              "Failed to release the FinGrind coordination control-file lock at "
                  + controlPath
                  + ".",
              failure);
        }
      } finally {
        closeLock.unlock();
      }
    }

    private void releaseRetainedControl() throws IOException {
      if (closeOperation != null) {
        closeOperation.close();
      } else {
        releaseLockAndFile(
            Objects.requireNonNull(lock, "lock"), Objects.requireNonNull(opened, "opened"));
      }
    }
  }
}
