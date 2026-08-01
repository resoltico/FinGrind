package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Exercises retained Windows lock lifecycle without invoking a Windows library. */
class WindowsPrivateOutputFileHandleTest {
  @Test
  void releasingALockUnlocksBeforeTheRetainedHandleCloses() throws Exception {
    RecordingCalls recording = new RecordingCalls();
    WindowsPrivateOutputFileHandle handle = opened(recording, 11L);

    try (handle;
        PrivateOutputFile.HeldLock ignored =
            Objects.requireNonNull(handle.tryExclusiveLock(0L, 1L))) {
      assertTrue(handle.isOpen());
    }

    assertEquals(List.of("LockFileEx", "UnlockFileEx", "CloseHandle"), recording.operations);
    assertFalse(handle.isOpen());
    assertThrows(IOException.class, handle::force);
  }

  @Test
  void closingTheHandleReleasesJvmRangeOwnershipWithoutUnlockingAnAlreadyClosedHandle()
      throws Exception {
    RecordingCalls recording = new RecordingCalls();
    WindowsPrivateOutputFileHandle handle = opened(recording, 12L);
    PrivateOutputFile.HeldLock retainedLock =
        Objects.requireNonNull(handle.tryExclusiveLock(0L, 1L));

    try (retainedLock;
        handle) {
      assertTrue(handle.isOpen());
    }
    handle.close();

    assertEquals(List.of("LockFileEx", "UnlockFileEx", "CloseHandle"), recording.operations);
    WindowsPrivateOutputFileHandle reopened = opened(new RecordingCalls(), 13L);
    try (reopened;
        PrivateOutputFile.HeldLock ignored =
            Objects.requireNonNull(reopened.tryExclusiveLock(0L, 1L))) {
      assertTrue(reopened.isOpen());
    }
  }

  @Test
  void nativeLockContentionReleasesTheLocalLeaseForALaterAttempt() throws Exception {
    RecordingCalls recording = new RecordingCalls();
    recording.lockResult =
        new WindowsPrivateOutputFileNative.Result<>(
            0, WindowsPrivateOutputFileNative.ERROR_LOCK_VIOLATION);
    WindowsPrivateOutputFileHandle handle = opened(recording, 14L);

    try (handle) {
      assertNull(handle.tryExclusiveLock(0L, 1L));
      recording.lockResult = new WindowsPrivateOutputFileNative.Result<>(1, 0);
      try (PrivateOutputFile.HeldLock ignored =
          Objects.requireNonNull(handle.tryExclusiveLock(0L, 1L))) {
        assertTrue(handle.isOpen());
      }
    }

    assertEquals(
        List.of("LockFileEx", "LockFileEx", "UnlockFileEx", "CloseHandle"), recording.operations);
  }

  @Test
  void readReportsEndOfFileWithoutAdvancingTheDestination() throws Exception {
    RecordingCalls recording = new RecordingCalls();

    try (WindowsPrivateOutputFileHandle handle = opened(recording, 15L)) {
      ByteBuffer destination = ByteBuffer.allocate(1);

      assertEquals(-1, handle.read(destination));
      assertEquals(0, destination.position());
    }

    assertEquals(List.of("ReadFile", "CloseHandle"), recording.operations);
  }

  @Test
  void readPreservesTheArenaCloseFailureAfterANativeEndOfFileProbe() throws Exception {
    RecordingCalls recording = new RecordingCalls();
    WindowsPrivateOutputFileHandle handle =
        new WindowsPrivateOutputFileHandle(
            recording.calls(),
            new WindowsPrivateOutputFileNative.Handle(16L),
            () -> new CloseFailingArena(Arena.ofConfined()));

    try (handle) {
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, () -> handle.read(ByteBuffer.allocate(1)));

      assertEquals("simulated arena close failure", failure.getMessage());
      assertTrue(handle.isOpen());
    }

    assertEquals(List.of("ReadFile", "CloseHandle"), recording.operations);
  }

  @Test
  void readFailsClosedWhenOperationArenaCreationReturnsNull() throws Exception {
    RecordingCalls recording = new RecordingCalls();

    try (WindowsPrivateOutputFileHandle handle =
        new WindowsPrivateOutputFileHandle(
            recording.calls(), new WindowsPrivateOutputFileNative.Handle(17L), () -> nullOf())) {
      assertThrows(NullPointerException.class, () -> handle.read(ByteBuffer.allocate(1)));
    }

    assertEquals(List.of("CloseHandle"), recording.operations);
  }

  @Test
  void lockingFailsClosedWhenItsOperationArenaCreationReturnsNull() throws Exception {
    RecordingCalls recording = new RecordingCalls();
    AtomicInteger arenaCount = new AtomicInteger();

    try (WindowsPrivateOutputFileHandle handle =
        new WindowsPrivateOutputFileHandle(
            recording.calls(),
            new WindowsPrivateOutputFileNative.Handle(18L),
            () -> arenaCount.incrementAndGet() == 2 ? nullOf() : Arena.ofConfined())) {
      assertThrows(NullPointerException.class, () -> handle.tryExclusiveLock(0L, 1L));

      try (PrivateOutputFile.HeldLock ignored =
          Objects.requireNonNull(handle.tryExclusiveLock(0L, 1L))) {
        assertTrue(handle.isOpen());
      }
    }

    assertEquals(List.of("LockFileEx", "UnlockFileEx", "CloseHandle"), recording.operations);
  }

  private static WindowsPrivateOutputFileHandle opened(RecordingCalls recording, long handleBits) {
    return new WindowsPrivateOutputFileHandle(
        recording.calls(), new WindowsPrivateOutputFileNative.Handle(handleBits));
  }

  /** Records the explicit Win32 calls used by the handle lifecycle test. */
  private static final class RecordingCalls
      extends WindowsPrivateOutputFileCallTestSupport.HandleCalls {
    private final List<String> operations = new ArrayList<>();
    private WindowsPrivateOutputFileNative.Result<Integer> lockResult =
        new WindowsPrivateOutputFileNative.Result<>(1, 0);
    private int readCount;

    private WindowsPrivateOutputFileCalls calls() {
      return new WindowsPrivateOutputFileCalls(
          this,
          new WindowsPrivateOutputFileCallTestSupport.OwnerCalls(),
          new WindowsPrivateOutputFileCallTestSupport.SecurityCalls());
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> getFileInformationByHandleEx(
        java.lang.foreign.MemorySegment handle,
        int informationClass,
        java.lang.foreign.MemorySegment information,
        int byteCount) {
      return new WindowsPrivateOutputFileNative.Result<>(1, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> readFile(
        MemorySegment handle,
        MemorySegment bytes,
        int byteCount,
        MemorySegment transferred,
        MemorySegment overlapped) {
      operations.add("ReadFile");
      transferred.set(ValueLayout.JAVA_INT, 0L, readCount);
      return new WindowsPrivateOutputFileNative.Result<>(1, 0);
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> lockFileEx(
        java.lang.foreign.MemorySegment handle,
        int flags,
        int reserved,
        int byteCountLow,
        int byteCountHigh,
        java.lang.foreign.MemorySegment overlapped) {
      operations.add("LockFileEx");
      return lockResult;
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> unlockFileEx(
        java.lang.foreign.MemorySegment handle,
        int reserved,
        int byteCountLow,
        int byteCountHigh,
        java.lang.foreign.MemorySegment overlapped) {
      return succeeded("UnlockFileEx");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(
        java.lang.foreign.MemorySegment handle) {
      return succeeded("CloseHandle");
    }

    @Override
    public WindowsPrivateOutputFileNative.Result<Integer> flushFileBuffers(
        java.lang.foreign.MemorySegment handle) {
      return succeeded("FlushFileBuffers");
    }

    private WindowsPrivateOutputFileNative.Result<Integer> succeeded(String operation) {
      operations.add(operation);
      return new WindowsPrivateOutputFileNative.Result<>(1, 0);
    }
  }

  /** Delegates native allocation while making a close failure deterministic for one read probe. */
  private record CloseFailingArena(Arena delegate) implements Arena {
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
      delegate.close();
      throw new IllegalStateException("simulated arena close failure");
    }
  }
}
