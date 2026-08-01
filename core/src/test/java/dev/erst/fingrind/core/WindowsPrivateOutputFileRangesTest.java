package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Exercises the JVM-local range ownership invariant independently of a native lock provider. */
class WindowsPrivateOutputFileRangesTest {
  @Test
  void reservesOnlyNonOverlappingRangesAndReleasesEachLeaseExactlyOnce() {
    WindowsPrivateOutputFileRanges.Lease first =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-normal", 4L, 3L));
    assertNull(WindowsPrivateOutputFileRanges.tryAcquire("ranges-normal", 5L, 1L));
    WindowsPrivateOutputFileRanges.Lease adjacent =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-normal", 7L, 2L));
    WindowsPrivateOutputFileRanges.Lease preceding =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-normal", 2L, 2L));

    first.close();
    first.close();
    adjacent.close();
    preceding.close();

    WindowsPrivateOutputFileRanges.Lease reopened =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-normal", 4L, 3L));
    reopened.close();
  }

  @Test
  void scopedAttemptReleasesUnlessTheRetainedHandleTakesOwnership() {
    WindowsPrivateOutputFileRanges.Lease released =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-attempt", 0L, 1L));
    WindowsPrivateOutputFileRanges.LeaseAttempt attempt =
        WindowsPrivateOutputFileRanges.LeaseAttempt.forLease(released);
    assertSame(released, attempt.lease());
    attempt.close();
    WindowsPrivateOutputFileRanges.Lease reacquired =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-attempt", 0L, 1L));
    reacquired.close();

    WindowsPrivateOutputFileRanges.Lease transferred =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-transferred", 0L, 1L));
    WindowsPrivateOutputFileRanges.LeaseAttempt transferredAttempt =
        WindowsPrivateOutputFileRanges.LeaseAttempt.forLease(transferred);
    transferredAttempt.transferToHandle();
    assertThrows(IllegalStateException.class, transferredAttempt::transferToHandle);
    transferredAttempt.close();
    assertNull(WindowsPrivateOutputFileRanges.tryAcquire("ranges-transferred", 0L, 1L));
    transferred.close();
  }

  @Test
  void reportsAnInvariantBreachWhenTheOwningRegistryLosesALiveLease() {
    WindowsPrivateOutputFileRanges.InMemoryRangeRegistry rangeRegistry =
        new WindowsPrivateOutputFileRanges.InMemoryRangeRegistry();
    WindowsPrivateOutputFileRanges.Lease lostLease =
        requireLease(
            WindowsPrivateOutputFileRanges.tryAcquire(rangeRegistry, "ranges-corrupted", 0L, 1L));
    WindowsPrivateOutputFileRanges.Lease liveLease =
        requireLease(
            WindowsPrivateOutputFileRanges.tryAcquire(rangeRegistry, "ranges-corrupted", 1L, 1L));
    assertTrue(rangeRegistry.release(lostLease));

    assertThrows(IllegalStateException.class, lostLease::close);
    liveLease.close();
  }

  @Test
  void reportsAnInvariantBreachWhenTheOwningRegistryLosesItsFinalLiveLease() {
    WindowsPrivateOutputFileRanges.InMemoryRangeRegistry rangeRegistry =
        new WindowsPrivateOutputFileRanges.InMemoryRangeRegistry();
    WindowsPrivateOutputFileRanges.Lease lease =
        requireLease(
            WindowsPrivateOutputFileRanges.tryAcquire(
                rangeRegistry, "ranges-corrupted-final", 0L, 1L));
    assertTrue(rangeRegistry.release(lease));

    assertThrows(IllegalStateException.class, lease::close);
  }

  @Test
  void retainedLockReleasesLocallyAfterItsHandleClosesAndNativelyWhileItRemainsOpen()
      throws Exception {
    AtomicInteger removals = new AtomicInteger();
    WindowsPrivateOutputFileRanges.Lease closedLease =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-closed-handle", 0L, 1L));
    try (WindowsPrivateOutputFileRetainedLock closedHandleLock =
        new WindowsPrivateOutputFileRetainedLock(
            new ReentrantLock(),
            () -> true,
            (position, size, lease) -> {
              throw new AssertionError("closed handles must not receive native unlock calls");
            },
            ignored -> removals.incrementAndGet(),
            0L,
            1L,
            closedLease)) {
      closedHandleLock.close();
      closedHandleLock.close();
    }
    assertEquals(1, removals.get());

    WindowsPrivateOutputFileRanges.Lease openLease =
        requireLease(WindowsPrivateOutputFileRanges.tryAcquire("ranges-open-handle", 0L, 1L));
    AtomicInteger nativeUnlocks = new AtomicInteger();
    try (WindowsPrivateOutputFileRetainedLock openHandleLock =
        new WindowsPrivateOutputFileRetainedLock(
            new ReentrantLock(),
            () -> false,
            (position, size, lease) -> {
              assertEquals(0L, position);
              assertEquals(1L, size);
              nativeUnlocks.incrementAndGet();
              lease.close();
            },
            ignored -> removals.incrementAndGet(),
            0L,
            1L,
            openLease)) {
      openHandleLock.close();
    }
    assertEquals(1, nativeUnlocks.get());
    assertEquals(2, removals.get());
  }

  private static WindowsPrivateOutputFileRanges.Lease requireLease(
      WindowsPrivateOutputFileRanges.@Nullable Lease lease) {
    assertNotNull(lease);
    return java.util.Objects.requireNonNull(lease, "lease");
  }
}
