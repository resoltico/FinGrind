package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/** Retained native and JVM-local range-lock lifecycle for one Windows private-output handle. */
final class WindowsPrivateOutputFileHandleLocks {
  private static final String LOCK_FILE_EX_OPERATION = "LockFileEx".formatted();
  private static final String UNLOCK_FILE_EX_OPERATION = "UnlockFileEx".formatted();
  private static final String CALLS_ARGUMENT = "calls".formatted();
  private static final String HANDLE_ARGUMENT = "handle".formatted();
  private static final String ARENA_FACTORY_ARGUMENT = "arenaFactory".formatted();
  private static final String LIFECYCLE_LOCK_ARGUMENT = "lifecycleLock".formatted();
  private static final String HANDLE_CLOSED_ARGUMENT = "handleClosed".formatted();
  private static final String RETAINED_LOCK_ARGUMENT = "retainedLock".formatted();
  private static final String VALUE_ARGUMENT = "value".formatted();

  private final WindowsPrivateOutputFileCalls calls;
  private final WindowsPrivateOutputFileNative.Handle handle;
  private final WindowsPrivateOutputFileHandle.ArenaFactory arenaFactory;
  private final ReentrantLock lifecycleLock;
  private final BooleanSupplier handleClosed;
  private final List<WindowsPrivateOutputFileRetainedLock> heldLocks = new ArrayList<>();

  WindowsPrivateOutputFileHandleLocks(
      WindowsPrivateOutputFileCalls calls,
      WindowsPrivateOutputFileNative.Handle handle,
      WindowsPrivateOutputFileHandle.ArenaFactory arenaFactory,
      ReentrantLock lifecycleLock,
      BooleanSupplier handleClosed) {
    this.calls = Objects.requireNonNull(calls, CALLS_ARGUMENT);
    this.handle = Objects.requireNonNull(handle, HANDLE_ARGUMENT);
    this.arenaFactory = Objects.requireNonNull(arenaFactory, ARENA_FACTORY_ARGUMENT);
    this.lifecycleLock = Objects.requireNonNull(lifecycleLock, LIFECYCLE_LOCK_ARGUMENT);
    this.handleClosed = Objects.requireNonNull(handleClosed, HANDLE_CLOSED_ARGUMENT);
  }

  /** Acquires one exact range lock while the retaining handle's lifecycle lock is already held. */
  PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(
      String physicalIdentity, long position, long size) throws IOException {
    WindowsPrivateOutputFileRanges.@Nullable Lease processLease =
        WindowsPrivateOutputFileRanges.tryAcquire(physicalIdentity, position, size);
    if (processLease == null) {
      return null;
    }
    WindowsPrivateOutputFileRanges.LeaseAttempt processLeaseAttempt =
        WindowsPrivateOutputFileRanges.LeaseAttempt.forLease(processLease);
    try {
      try (RetainedLockAdmission admission = new RetainedLockAdmission()) {
        try (WindowsPrivateOutputFileOperationArena operationArena =
            new WindowsPrivateOutputFileOperationArena(arenaFactory.create())) {
          WindowsPrivateOutputFileNative.Result<Integer> lockResult =
              calls
                  .fileCalls()
                  .lockFileEx(
                      handle.segment(),
                      WindowsPrivateOutputFileNative.LOCKFILE_EXCLUSIVE_LOCK
                          | WindowsPrivateOutputFileNative.LOCKFILE_FAIL_IMMEDIATELY,
                      0,
                      WindowsPrivateOutputFileNative.lowDword(size),
                      WindowsPrivateOutputFileNative.highDword(size),
                      WindowsPrivateOutputFileNative.zeroedOverlapped(
                          operationArena.arena(), position));
          if (lockResult.value() == 0) {
            if (lockResult.lastError() == WindowsPrivateOutputFileNative.ERROR_LOCK_VIOLATION) {
              return null;
            } else {
              throw WindowsPrivateOutputFileNative.windowsFailure(
                  LOCK_FILE_EX_OPERATION, lockResult.lastError());
            }
          }
          admission.accept(
              new WindowsPrivateOutputFileRetainedLock(
                  lifecycleLock,
                  handleClosed,
                  this::unlockAndRelease,
                  heldLocks::remove,
                  position,
                  size,
                  processLease));
        }
        processLeaseAttempt.transferToHandle();
        heldLocks.add(admission.retainedLock());
        return admission.transfer();
      }
    } finally {
      processLeaseAttempt.close();
    }
  }

  // Releases every retained range lock before the native handle closes.
  void releaseAll() throws IOException {
    while (!heldLocks.isEmpty()) {
      heldLocks.getFirst().close();
    }
  }

  private void unlockAndRelease(
      long position, long size, WindowsPrivateOutputFileRanges.Lease lease) throws IOException {
    IOException failure = null;
    try (WindowsPrivateOutputFileOperationArena operationArena =
        new WindowsPrivateOutputFileOperationArena(arenaFactory.create())) {
      WindowsPrivateOutputFileNative.requireTrue(
          calls
              .fileCalls()
              .unlockFileEx(
                  handle.segment(),
                  0,
                  WindowsPrivateOutputFileNative.lowDword(size),
                  WindowsPrivateOutputFileNative.highDword(size),
                  WindowsPrivateOutputFileNative.zeroedOverlapped(
                      operationArena.arena(), position)),
          UNLOCK_FILE_EX_OPERATION);
    } catch (IOException exception) {
      failure = exception;
    } finally {
      lease.close();
    }
    if (failure != null) {
      throw failure;
    }
  }

  /** Owns a newly created retained lock until the handle list accepts it after arena cleanup. */
  private static final class RetainedLockAdmission implements AutoCloseable {
    private @Nullable WindowsPrivateOutputFileRetainedLock retainedLock;

    void accept(WindowsPrivateOutputFileRetainedLock value) {
      retainedLock = Objects.requireNonNull(value, VALUE_ARGUMENT);
    }

    WindowsPrivateOutputFileRetainedLock retainedLock() {
      return Objects.requireNonNull(retainedLock, RETAINED_LOCK_ARGUMENT);
    }

    PrivateOutputFile.HeldLock transfer() {
      PrivateOutputFile.HeldLock transferred = retainedLock();
      retainedLock = null;
      return transferred;
    }

    @Override
    public void close() throws IOException {
      if (retainedLock != null) {
        try {
          Objects.requireNonNull(retainedLock, RETAINED_LOCK_ARGUMENT).close();
        } finally {
          retainedLock = null;
        }
      }
    }
  }
}
