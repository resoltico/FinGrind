package dev.erst.fingrind.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** JVM-local physical-file range ownership for the Windows locking capability. */
final class WindowsPrivateOutputFileRanges {
  private static final RangeRegistry PROCESS_RANGE_REGISTRY = new InMemoryRangeRegistry();

  private WindowsPrivateOutputFileRanges() {}

  static @Nullable Lease tryAcquire(String physicalIdentity, long position, long size) {
    return tryAcquire(PROCESS_RANGE_REGISTRY, physicalIdentity, position, size);
  }

  /** Reserves one range through the supplied ownership registry. */
  static @Nullable Lease tryAcquire(
      RangeRegistry rangeRegistry, String physicalIdentity, long position, long size) {
    RangeRegistry checkedRangeRegistry = Objects.requireNonNull(rangeRegistry, "rangeRegistry");
    String checkedIdentity = Objects.requireNonNull(physicalIdentity, "physicalIdentity");
    PrivateOutputFile.requireLockRange(position, size);
    long endExclusive = position + size;
    Lease lease = new Lease(checkedRangeRegistry, checkedIdentity, position, endExclusive);
    return checkedRangeRegistry.tryReserve(lease) ? lease : null;
  }

  /** Registry that atomically reserves and releases JVM-local physical-file ranges. */
  interface RangeRegistry {
    /** Returns whether this range was reserved without overlapping a live reservation. */
    boolean tryReserve(Lease lease);

    /** Returns whether this exact live reservation was released. */
    boolean release(Lease lease);
  }

  /**
   * Process-local reservation map serialized by one explicit lock across overlap detection and
   * mutation.
   *
   * @implNote The concurrent map prevents accidental unsafe access if future diagnostics need a
   *     snapshot outside the reservation critical section.
   */
  static final class InMemoryRangeRegistry implements RangeRegistry {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, List<Lease>> heldRanges = new ConcurrentHashMap<>();

    @Override
    public boolean tryReserve(Lease lease) {
      lock.lock();
      try {
        List<Lease> leases =
            heldRanges.computeIfAbsent(lease.physicalIdentity, ignored -> new ArrayList<>());
        for (Lease held : leases) {
          if (lease.position < held.endExclusive && held.position < lease.endExclusive) {
            return false;
          }
        }
        leases.add(lease);
        return true;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public boolean release(Lease lease) {
      lock.lock();
      try {
        List<Lease> leases = heldRanges.get(lease.physicalIdentity);
        if (leases == null || !leases.remove(lease)) {
          return false;
        }
        if (leases.isEmpty()) {
          heldRanges.remove(lease.physicalIdentity, leases);
        }
        return true;
      } finally {
        lock.unlock();
      }
    }
  }

  /** One JVM-local physical-file range proof, removed exactly once. */
  static final class Lease {
    private final RangeRegistry rangeRegistry;
    private final String physicalIdentity;
    private final long position;
    private final long endExclusive;
    private final ReentrantLock closeLock = new ReentrantLock();
    private boolean closed;

    private Lease(
        RangeRegistry rangeRegistry, String physicalIdentity, long position, long endExclusive) {
      this.rangeRegistry = Objects.requireNonNull(rangeRegistry, "rangeRegistry");
      this.physicalIdentity = Objects.requireNonNull(physicalIdentity, "physicalIdentity");
      this.position = position;
      this.endExclusive = endExclusive;
    }

    void close() {
      closeLock.lock();
      try {
        if (closed) {
          return;
        }
        closed = true;
        if (!rangeRegistry.release(this)) {
          throw new IllegalStateException(
              "Windows private-output range ownership changed unexpectedly.");
        }
      } finally {
        closeLock.unlock();
      }
    }
  }

  /**
   * Scoped native-lock attempt that releases its range reservation unless ownership is transferred.
   */
  static final class LeaseAttempt {
    private final Lease lease;
    private boolean transferred;

    private LeaseAttempt(Lease lease) {
      this.lease = Objects.requireNonNull(lease, "lease");
    }

    /** Starts a scoped native-lock attempt for one previously reserved JVM-local range. */
    static LeaseAttempt forLease(Lease lease) {
      return new LeaseAttempt(lease);
    }

    /** Returns the reservation that remains live while this attempt owns its cleanup. */
    Lease lease() {
      return lease;
    }

    /** Transfers cleanup responsibility to the retained handle returned by a successful attempt. */
    void transferToHandle() {
      if (transferred) {
        throw new IllegalStateException(
            "Windows private-output range ownership changed unexpectedly.");
      }
      transferred = true;
    }

    /** Releases the reservation unless a retained handle accepted ownership. */
    void close() {
      if (!transferred) {
        lease.close();
      }
    }
  }
}
