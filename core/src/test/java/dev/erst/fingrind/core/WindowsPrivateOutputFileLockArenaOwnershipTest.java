package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Proves retained lock ownership survives a failure while closing its dedicated native arena. */
class WindowsPrivateOutputFileLockArenaOwnershipTest {
  @Test
  void retainedHandleReleasesEveryLockOwnershipPathWhenItsPostLockArenaCloseFails()
      throws Exception {
    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      AtomicInteger arenaCount = new AtomicInteger();
      try (WindowsPrivateOutputFileHandle handle =
          new WindowsPrivateOutputFileHandle(
              windows.calls(),
              new WindowsPrivateOutputFileNative.Handle(0x77L),
              () ->
                  new WindowsPrivateOutputFileCloseFailingArena(
                      Arena.ofConfined(), arenaCount.incrementAndGet() == 2))) {
        assertThrows(IllegalStateException.class, () -> handle.tryExclusiveLock(8L, 1L));
        try (PrivateOutputFile.HeldLock recovered =
            Objects.requireNonNull(handle.tryExclusiveLock(8L, 1L))) {
          assertTrue(handle.isOpen());
          recovered.close();
        }
      }
    }

    try (WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32 windows =
        new WindowsPrivateOutputFileFfmTransportTest.SyntheticWin32()) {
      windows.lockFailure(WindowsPrivateOutputFileNative.ERROR_LOCK_VIOLATION);
      AtomicInteger arenaCount = new AtomicInteger();
      try (WindowsPrivateOutputFileHandle handle =
          new WindowsPrivateOutputFileHandle(
              windows.calls(),
              new WindowsPrivateOutputFileNative.Handle(0x77L),
              () ->
                  new WindowsPrivateOutputFileCloseFailingArena(
                      Arena.ofConfined(), arenaCount.incrementAndGet() == 2))) {
        assertThrows(IllegalStateException.class, () -> handle.tryExclusiveLock(9L, 1L));
      }
    }
  }
}
