package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Retained direct Windows file handle, descriptor proof, I/O, identity, and range-lock behavior.
 */
final class WindowsPrivateOutputFileHandle implements WindowsPrivateOutputFileTransport.NativeFile {
  private final WindowsPrivateOutputFileCalls calls;
  private final WindowsPrivateOutputFileNative.Handle handle;
  private final ArenaFactory arenaFactory;
  private final ReentrantLock lifecycleLock = new ReentrantLock();
  private final WindowsPrivateOutputFileHandleLocks locks;
  private boolean closed;

  WindowsPrivateOutputFileHandle(
      WindowsPrivateOutputFileCalls calls, WindowsPrivateOutputFileNative.Handle handle) {
    this(calls, handle, Arena::ofConfined);
  }

  WindowsPrivateOutputFileHandle(
      WindowsPrivateOutputFileCalls calls,
      WindowsPrivateOutputFileNative.Handle handle,
      ArenaFactory arenaFactory) {
    this.calls = Objects.requireNonNull(calls, "calls");
    this.handle = Objects.requireNonNull(handle, "handle");
    this.arenaFactory = Objects.requireNonNull(arenaFactory, "arenaFactory");
    this.locks =
        new WindowsPrivateOutputFileHandleLocks(
            this.calls, this.handle, this.arenaFactory, lifecycleLock, () -> closed);
  }

  @Override
  public WindowsPrivateOutputFileTransport.SecurityProof securityProof(
      WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      WindowsPrivateOutputFileTransport.CurrentTokenUser checkedTokenUser =
          Objects.requireNonNull(tokenUser, "tokenUser");
      if (!WindowsPrivateOutputFileOwner.class.isInstance(checkedTokenUser)) {
        throw new IllegalArgumentException(
            "The Windows private-output handle received an incompatible owner context.");
      }
      return WindowsPrivateOutputFileSecurityProof.read(
          calls, handle, WindowsPrivateOutputFileOwner.class.cast(checkedTokenUser).ownerSid());
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public int read(ByteBuffer destination) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      ByteBuffer checkedDestination = Objects.requireNonNull(destination, "destination");
      int requested =
          Math.min(
              checkedDestination.remaining(),
              WindowsPrivateOutputFileNative.MAXIMUM_TRANSFER_BYTES);
      if (requested == 0) {
        return 0;
      }
      try (WindowsPrivateOutputFileOperationArena operationArena =
          new WindowsPrivateOutputFileOperationArena(arenaFactory.create())) {
        MemorySegment bytes = operationArena.arena().allocate(requested, Byte.BYTES);
        MemorySegment count = operationArena.arena().allocate(ValueLayout.JAVA_INT);
        WindowsPrivateOutputFileNative.requireTrue(
            calls
                .fileCalls()
                .readFile(handle.segment(), bytes, requested, count, MemorySegment.NULL),
            "ReadFile");
        int read = count.get(ValueLayout.JAVA_INT, 0L);
        if (read < 0 || read > requested) {
          throw new IOException("ReadFile returned an invalid byte count.");
        }
        if (read == 0) {
          return -1;
        }
        checkedDestination.put(bytes.asSlice(0L, read).toArray(ValueLayout.JAVA_BYTE));
        return read;
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public int write(ByteBuffer source) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      ByteBuffer checkedSource = Objects.requireNonNull(source, "source");
      int requested =
          Math.min(
              checkedSource.remaining(), WindowsPrivateOutputFileNative.MAXIMUM_TRANSFER_BYTES);
      if (requested == 0) {
        return 0;
      }
      byte[] copied = new byte[requested];
      checkedSource.duplicate().get(copied);
      try (Arena arena = arenaFactory.create()) {
        MemorySegment bytes = arena.allocateFrom(ValueLayout.JAVA_BYTE, copied);
        MemorySegment count = arena.allocate(ValueLayout.JAVA_INT);
        WindowsPrivateOutputFileNative.requireTrue(
            calls
                .fileCalls()
                .writeFile(handle.segment(), bytes, requested, count, MemorySegment.NULL),
            "WriteFile");
        int written = count.get(ValueLayout.JAVA_INT, 0L);
        if (written <= 0 || written > requested) {
          throw new IOException("WriteFile did not make valid write progress.");
        }
        checkedSource.position(checkedSource.position() + written);
        return written;
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  boolean isOpen() {
    lifecycleLock.lock();
    try {
      return !closed;
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public long size() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      try (Arena arena = arenaFactory.create()) {
        MemorySegment size = arena.allocate(ValueLayout.JAVA_LONG);
        WindowsPrivateOutputFileNative.requireTrue(
            calls.fileCalls().getFileSizeEx(handle.segment(), size), "GetFileSizeEx");
        return size.get(ValueLayout.JAVA_LONG, 0L);
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void truncate(long size) throws IOException {
    if (size < 0L) {
      throw new IllegalArgumentException("size must be non-negative.");
    }
    lifecycleLock.lock();
    try {
      position(size);
      WindowsPrivateOutputFileNative.requireTrue(
          calls.fileCalls().setEndOfFile(handle.segment()), "SetEndOfFile");
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void position(long position) throws IOException {
    if (position < 0L) {
      throw new IllegalArgumentException("position must be non-negative.");
    }
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      WindowsPrivateOutputFileNative.requireTrue(
          calls
              .fileCalls()
              .setFilePointerEx(
                  handle.segment(),
                  position,
                  MemorySegment.NULL,
                  WindowsPrivateOutputFileNative.FILE_BEGIN),
          "SetFilePointerEx");
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void force() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      WindowsPrivateOutputFileNative.requireTrue(
          calls.fileCalls().flushFileBuffers(handle.segment()), "FlushFileBuffers");
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
      throws IOException {
    PrivateOutputFile.requireLockRange(position, size);
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return locks.tryExclusiveLock(physicalObjectIdentityLocked(), position, size);
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public String physicalObjectIdentity() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return physicalObjectIdentityLocked();
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    lifecycleLock.lock();
    try {
      if (closed) {
        return;
      }
      try {
        locks.releaseAll();
      } catch (IOException | RuntimeException | Error failure) {
        closeAfterLockReleaseFailure(failure);
        throw failure;
      }
      closed = true;
      WindowsPrivateOutputFileNative.closeHandle(calls.fileCalls(), handle);
    } finally {
      lifecycleLock.unlock();
    }
  }

  /**
   * Closes this native handle while retaining the primary failure as the operation's outcome.
   *
   * @param failure primary operation failure that receives cleanup failures as suppressed causes
   */
  void closePreservingFailure(Throwable failure) {
    Throwable primaryFailure = Objects.requireNonNull(failure);
    lifecycleLock.lock();
    try {
      if (closed) {
        return;
      }
      try {
        locks.releaseAll();
      } catch (IOException | RuntimeException | Error releaseFailure) {
        primaryFailure.addSuppressed(releaseFailure);
      }
      closed = true;
      WindowsPrivateOutputFileNative.closePreservingFailure(
          calls.fileCalls(), handle, primaryFailure);
    } finally {
      lifecycleLock.unlock();
    }
  }

  private void closeAfterLockReleaseFailure(Throwable failure) {
    closed = true;
    WindowsPrivateOutputFileNative.closePreservingFailure(calls.fileCalls(), handle, failure);
  }

  /**
   * Closes one known resource while preserving the operation failure that triggered cleanup.
   *
   * @param resource resource to close
   * @param failure primary operation failure that receives cleanup failures as suppressed causes
   */
  static void closePreservingFailure(AutoCloseable resource, Throwable failure) {
    try {
      Objects.requireNonNull(resource).close();
    } catch (Exception | Error closeFailure) {
      Objects.requireNonNull(failure).addSuppressed(closeFailure);
    }
  }

  /**
   * Allocates one short-lived native arena for a retained-handle operation.
   *
   * @implNote Each operation receives distinct confined native memory.
   */
  @FunctionalInterface
  interface ArenaFactory {
    /**
     * Returns one fresh confined arena.
     *
     * @return a new short-lived native arena
     */
    Arena create();
  }

  private String physicalObjectIdentityLocked() throws IOException {
    try (Arena arena = arenaFactory.create()) {
      MemorySegment info = arena.allocate(24L, Long.BYTES);
      WindowsPrivateOutputFileNative.requireTrue(
          calls
              .fileCalls()
              .getFileInformationByHandleEx(
                  handle.segment(), WindowsPrivateOutputFileNative.FILE_ID_INFO, info, 24),
          "GetFileInformationByHandleEx(FileIdInfo)");
      return "windows-v1:volume="
          + Long.toUnsignedString(info.get(ValueLayout.JAVA_LONG, 0L))
          + ":file="
          + HexFormat.of().formatHex(info.asSlice(Long.BYTES, 16L).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  private void requireOpenLocked() throws IOException {
    if (closed) {
      throw new IOException("The retained Windows private-output handle is already closed.");
    }
  }
}
