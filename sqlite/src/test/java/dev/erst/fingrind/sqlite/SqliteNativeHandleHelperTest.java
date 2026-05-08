package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Tests the deterministic native-handle helper doubles used by split SQLite bridge suites. */
class SqliteNativeHandleHelperTest {
  @Test
  void nativeHandleTargets_areDirectlyCallable() {
    AtomicInteger shutdownCalls = new AtomicInteger();
    AtomicInteger closeCalls = new AtomicInteger();
    assertEquals(1, SqliteNativeBridgeTestSupport.recordShutdownCall(shutdownCalls));
    assertEquals(
        0, SqliteNativeBridgeTestSupport.recordCloseCall(closeCalls, MemorySegment.ofAddress(1)));
    assertEquals(14, SqliteNativeBridgeTestSupport.recordCloseCall(closeCalls, MemorySegment.NULL));
    IllegalStateException closeException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeBridgeTestSupport.recordCloseCallThenThrow(
                    new AtomicInteger(), MemorySegment.NULL));
    assertEquals("close boom for null", closeException.getMessage());
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment openedHandle = MemorySegment.ofAddress(7);
      assertEquals(
          0,
          SqliteNativeBridgeTestSupport.openWithDatabaseHandle(
              openedHandle, MemorySegment.NULL, pointer, 0, MemorySegment.NULL));
      assertEquals(openedHandle, pointer.get(ValueLayout.ADDRESS, 0));
      assertEquals(
          14,
          SqliteNativeBridgeTestSupport.failOpenWithDatabaseHandle(
              openedHandle, MemorySegment.NULL, pointer, 0, MemorySegment.NULL));
      assertEquals(openedHandle, pointer.get(ValueLayout.ADDRESS, 0));
    }
  }

  @Test
  void retriableCloseHelpers_delegateToRealCloseAfterTheFirstFailure() {
    AtomicInteger simulatedCloseCalls = new AtomicInteger();
    AtomicInteger delegatedCloseCalls = new AtomicInteger();
    SqliteNativeCalls.AddressToIntCall delegateClose =
        ignored -> {
          delegatedCloseCalls.incrementAndGet();
          return 0;
        };
    assertEquals(
        14,
        SqliteNativeBridgeTestSupport.failThenDelegateCloseCall(
            simulatedCloseCalls, delegateClose, MemorySegment.NULL));
    assertEquals(0, delegatedCloseCalls.get());
    assertEquals(
        0,
        SqliteNativeBridgeTestSupport.failThenDelegateCloseCall(
            simulatedCloseCalls, delegateClose, MemorySegment.NULL));
    assertEquals(1, delegatedCloseCalls.get());
  }

  @Test
  void throwingRetriableCloseHelpers_delegateToRealCloseAfterRetry() {
    AtomicInteger delegatedCloseCalls = new AtomicInteger();
    SqliteNativeCalls.AddressToIntCall delegateClose =
        ignored -> {
          delegatedCloseCalls.incrementAndGet();
          return 0;
        };
    IllegalStateException runtimeFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteNativeBridgeTestSupport.throwIllegalStateThenDelegateCloseCall(
                    new AtomicInteger(), delegateClose, MemorySegment.NULL));
    assertEquals("boom", runtimeFailure.getMessage());
    AssertionError errorFailure =
        assertThrows(
            AssertionError.class,
            () ->
                SqliteNativeBridgeTestSupport.throwAssertionThenDelegateCloseCall(
                    new AtomicInteger(), delegateClose, MemorySegment.NULL));
    assertEquals("boom", errorFailure.getMessage());
    assertEquals(0, delegatedCloseCalls.get());
    AtomicInteger runtimeRetryCalls = new AtomicInteger(1);
    AtomicInteger errorRetryCalls = new AtomicInteger(1);
    assertEquals(
        0,
        SqliteNativeBridgeTestSupport.throwIllegalStateThenDelegateCloseCall(
            runtimeRetryCalls, delegateClose, MemorySegment.NULL));
    assertEquals(
        0,
        SqliteNativeBridgeTestSupport.throwAssertionThenDelegateCloseCall(
            errorRetryCalls, delegateClose, MemorySegment.NULL));
    assertEquals(2, delegatedCloseCalls.get());
  }
}
