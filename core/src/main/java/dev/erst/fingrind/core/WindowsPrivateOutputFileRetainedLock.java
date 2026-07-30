package dev.erst.fingrind.core;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Exact Windows range lock retained with a native handle until explicit release or handle close.
 */
final class WindowsPrivateOutputFileRetainedLock implements PrivateOutputFile.HeldLock {
  private final ReentrantLock lifecycleLock;
  private final BooleanSupplier handleClosed;
  private final NativeUnlock nativeUnlock;
  private final Consumer<WindowsPrivateOutputFileRetainedLock> removal;
  private final long position;
  private final long size;
  private final WindowsPrivateOutputFileRanges.Lease lease;
  private boolean released;

  WindowsPrivateOutputFileRetainedLock(
      ReentrantLock lifecycleLock,
      BooleanSupplier handleClosed,
      NativeUnlock nativeUnlock,
      Consumer<WindowsPrivateOutputFileRetainedLock> removal,
      long position,
      long size,
      WindowsPrivateOutputFileRanges.Lease lease) {
    this.lifecycleLock = Objects.requireNonNull(lifecycleLock, "lifecycleLock");
    this.handleClosed = Objects.requireNonNull(handleClosed, "handleClosed");
    this.nativeUnlock = Objects.requireNonNull(nativeUnlock, "nativeUnlock");
    this.removal = Objects.requireNonNull(removal, "removal");
    this.position = position;
    this.size = size;
    this.lease = Objects.requireNonNull(lease, "lease");
  }

  /** Releases this lock once, using native unlock while the retaining handle remains open. */
  @Override
  public void close() throws IOException {
    lifecycleLock.lock();
    try {
      if (released) {
        return;
      }
      released = true;
      try {
        if (handleClosed.getAsBoolean()) {
          lease.close();
        } else {
          nativeUnlock.unlock(position, size, lease);
        }
      } finally {
        removal.accept(this);
      }
    } finally {
      lifecycleLock.unlock();
    }
  }

  /** Performs the native unlock and releases the associated JVM-local reservation. */
  @FunctionalInterface
  interface NativeUnlock {
    /** Unlocks and releases the exact range reservation. */
    void unlock(long position, long size, WindowsPrivateOutputFileRanges.Lease lease)
        throws IOException;
  }
}
