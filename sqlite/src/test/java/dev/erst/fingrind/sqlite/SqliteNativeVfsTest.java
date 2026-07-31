package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/** Proves native SQLite opens select its long-path VFS only for Windows. */
class SqliteNativeVfsTest {
  @Test
  void selectsTheLongPathVfsForWindowsAndTheNativeDefaultElsewhere() {
    assertEquals("win32-longpath", SqliteNativeVfs.openVfsName("Windows Server 2022"));
    assertNull(SqliteNativeVfs.openVfsName("Mac OS X"));
    assertNull(SqliteNativeVfs.openVfsName("Linux"));

    try (Arena arena = Arena.ofConfined()) {
      assertEquals("win32-longpath", SqliteNativeVfs.openVfs(arena, "Windows 11").getString(0));
      assertEquals(MemorySegment.NULL, SqliteNativeVfs.openVfs(arena, "Linux"));
    }
  }

  @Test
  void requireRegistered_rejectsAnUnavailableRequiredVfs() {
    try (Arena arena = Arena.ofConfined()) {
      assertDoesNotThrow(
          () -> SqliteNativeVfs.requireRegistered("win32-longpath", arena.allocate(1)));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteNativeVfs.requireRegistered("win32-longpath", MemorySegment.NULL));

      assertEquals(
          "The loaded SQLite runtime does not provide the required VFS 'win32-longpath'.",
          exception.getMessage());
    }
  }
}
