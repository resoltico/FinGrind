package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises FFM transport cleanup ordering when native work and arena closure both fail. */
class WindowsPrivateOutputFileFfmTransportResourceLifecycleTest {
  @Test
  void retainedHandleCleanupPreservesItsPrimaryFailureWhenOneResourceAlsoFailsToClose()
      throws Exception {
    IOException primary = new IOException("primary failure");
    IOException cleanup = new IOException("cleanup failure");

    WindowsPrivateOutputFileHandle.closePreservingFailure(() -> {}, primary);
    WindowsPrivateOutputFileHandle.closePreservingFailure(
        () -> {
          throw cleanup;
        },
        primary);

    assertEquals(1, primary.getSuppressed().length);
    assertEquals(cleanup, primary.getSuppressed()[0]);
  }

  @Test
  void retainedHandlePreservesNativeFailuresWhenItsOperationArenaAlsoFailsToClose()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
            new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32();
        WindowsPrivateOutputFileHandle handle =
            windows.handle(() -> new ClosingArena(Arena.ofConfined(), true))) {
      assertThrows(IllegalStateException.class, () -> handle.read(ByteBuffer.allocate(1)));
    }

    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
            new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32();
        WindowsPrivateOutputFileHandle handle =
            windows.handle(() -> new ClosingArena(Arena.ofConfined(), true))) {
      windows.readCount(2);
      IOException failure =
          assertThrows(IOException.class, () -> handle.read(ByteBuffer.allocate(1)));
      assertEquals(1, failure.getSuppressed().length);
    }

    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      windows.lockFailure(5);
      AtomicInteger arenaCount = new AtomicInteger();
      try (WindowsPrivateOutputFileHandle handle =
          windows.handle(
              () -> new ClosingArena(Arena.ofConfined(), arenaCount.incrementAndGet() == 2))) {
        IOException failure =
            assertThrows(IOException.class, () -> handle.tryExclusiveLock(0L, 1L));
        assertEquals(1, failure.getSuppressed().length);
      }
    }

    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      windows.unlockFailure(6);
      AtomicInteger arenaCount = new AtomicInteger();
      try (WindowsPrivateOutputFileHandle handle =
          windows.handle(
              () -> new ClosingArena(Arena.ofConfined(), arenaCount.incrementAndGet() == 2))) {
        IllegalStateException failure =
            assertThrows(IllegalStateException.class, () -> handle.tryExclusiveLock(0L, 1L));
        assertEquals(1, failure.getSuppressed().length);
      }
    }
  }

  @Test
  void retainedHandleCleanupReleasesLocksAndRemainsClosedAfterFailurePreservation()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
            new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32();
        WindowsPrivateOutputFileHandle handle = windows.handle();
        PrivateOutputFile.HeldLock heldLock =
            Objects.requireNonNull(handle.tryExclusiveLock(0L, 1L))) {
      windows.unlockFailure(6);
      RuntimeException primary = new RuntimeException("primary");
      handle.closePreservingFailure(primary);
      assertEquals(1, primary.getSuppressed().length);

      RuntimeException laterPrimary = new RuntimeException("later primary");
      handle.closePreservingFailure(laterPrimary);
      assertEquals(0, laterPrimary.getSuppressed().length);
      heldLock.close();
    }
  }

  @Test
  void retainedHandleRejectsUnavailableOperationArenasBeforeNativeOwnershipCanEscape()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
            new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32();
        WindowsPrivateOutputFileHandle handle = windows.handle(() -> nullOf())) {
      assertThrows(NullPointerException.class, () -> handle.read(ByteBuffer.allocate(1)));
    }

    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      AtomicInteger arenaCount = new AtomicInteger();
      try (WindowsPrivateOutputFileHandle handle =
          windows.handle(() -> arenaCount.incrementAndGet() == 1 ? Arena.ofConfined() : nullOf())) {
        assertThrows(NullPointerException.class, () -> handle.tryExclusiveLock(0L, 1L));
      }
    }
  }

  /** Delegates allocation while making close failure deterministic and idempotent. */
  private static final class ClosingArena implements Arena {
    private final Arena delegate;
    private final boolean failOnClose;
    private boolean closed;

    private ClosingArena(Arena delegate, boolean failOnClose) {
      this.delegate = Objects.requireNonNull(delegate, "delegate");
      this.failOnClose = failOnClose;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
      return delegate.allocate(byteSize, byteAlignment);
    }

    @Override
    public MemorySegment.Scope scope() {
      return delegate.scope();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      delegate.close();
      if (failOnClose) {
        throw new IllegalStateException("simulated arena close failure");
      }
    }
  }
}
