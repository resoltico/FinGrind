package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Tests the deterministic native-handle helper doubles used by split SQLite bridge suites. */
@NullUnmarked
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
}
