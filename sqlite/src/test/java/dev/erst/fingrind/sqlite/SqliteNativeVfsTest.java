package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import org.junit.jupiter.api.Test;

/** Proves native SQLite opens select the encrypted long-path VFS only for Windows. */
class SqliteNativeVfsTest {
  @Test
  void openFilename_usesExtendedNamespaceOnWindows() {
    assertEquals(
        "\\\\?\\C:\\books\\protected.sqlite",
        SqliteNativeVfs.openFilename("C:\\books\\protected.sqlite", "Windows Server 2022"));
  }

  @Test
  void openFilename_preservesAnExtendedWindowsNamespace() {
    assertEquals(
        "\\\\?\\C:\\books\\protected.sqlite",
        SqliteNativeVfs.openFilename("\\\\?\\C:\\books\\protected.sqlite", "Windows Server 2022"));
  }

  @Test
  void openFilename_translatesAWindowsUncPath() {
    assertEquals(
        "\\\\?\\UNC\\server\\share\\protected.sqlite",
        SqliteNativeVfs.openFilename("\\\\server\\share\\protected.sqlite", "Windows Server 2022"));
  }

  @Test
  void openFilename_leavesNonWindowsPathsUnchanged() {
    assertEquals(
        "/books/protected.sqlite",
        SqliteNativeVfs.openFilename("/books/protected.sqlite", "Linux"));
  }

  @Test
  void selectsTheLongPathVfsForWindowsAndTheNativeDefaultElsewhere() {
    assertEquals(
        "multipleciphers-win32-longpath", SqliteNativeVfs.openVfsName("Windows Server 2022"));
    assertNull(SqliteNativeVfs.openVfsName("Mac OS X"));
    assertNull(SqliteNativeVfs.openVfsName("Linux"));

    try (Arena arena = Arena.ofConfined()) {
      assertEquals(
          "multipleciphers-win32-longpath",
          SqliteNativeVfs.openVfs(arena, "Windows 11").getString(0));
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

      assertThrows(
          IllegalArgumentException.class,
          () -> SqliteNativeVfs.requireRegistered(" ", arena.allocate(1)));
    }
  }

  @Test
  void requireSelectedVfsAvailable_queriesOnlyTheWindowsLongPathVfs() {
    try (Arena arena = Arena.ofConfined()) {
      assertDoesNotThrow(
          () ->
              SqliteNativeVfs.requireSelectedVfsAvailable(
                  "Windows Server 2025",
                  vfsNamePointer -> {
                    assertEquals("win32-longpath", vfsNamePointer.getString(0));
                    return arena.allocate(1);
                  }));
      assertDoesNotThrow(
          () ->
              SqliteNativeVfs.requireSelectedVfsAvailable(
                  "Linux",
                  ignored -> {
                    throw new AssertionError("Non-Windows hosts must use SQLite's native default.");
                  }));
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteNativeVfs.requireSelectedVfsAvailable(
                  "Windows 11", ignored -> MemorySegment.NULL));
    }
  }

  @Test
  void requireHostVfsAvailable_bindsTheVerifiedManagedLibraryVfs() {
    SqliteNativeApi sqliteApi = SqliteNativeBootstrap.api();
    try (Arena lookupArena = Arena.ofConfined()) {
      SymbolLookup lookup = SymbolLookup.libraryLookup(sqliteApi.loadedLibraryPath(), lookupArena);
      if (SqliteCoordinationControlProtocol.isWindows()) {
        assertDoesNotThrow(() -> SqliteNativeVfs.requireHostVfsAvailable("Windows", lookup));
      } else {
        assertThrows(
            IllegalStateException.class,
            () -> SqliteNativeVfs.requireHostVfsAvailable("Windows", lookup));
      }
    }
  }
}
