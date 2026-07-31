package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Selects the managed SQLite VFS that preserves the host filesystem's supported path range. */
final class SqliteNativeVfs {
  private static final String WINDOWS_LONG_PATH_VFS = "win32-longpath";

  private SqliteNativeVfs() {}

  /**
   * Allocates the explicit VFS selector required by the current host, or SQLite's native default.
   */
  static MemorySegment openVfs(Arena arena) {
    return openVfs(arena, System.getProperty("os.name", ""));
  }

  /** Allocates the explicit VFS selector for one supported operating-system name. */
  static MemorySegment openVfs(Arena arena, String operatingSystemName) {
    Arena checkedArena = Objects.requireNonNull(arena, "arena");
    @Nullable String vfsName = openVfsName(operatingSystemName);
    return vfsName == null ? MemorySegment.NULL : checkedArena.allocateFrom(vfsName);
  }

  /** Returns the explicit VFS required to avoid SQLite's classic Windows path ceiling. */
  static @Nullable String openVfsName(String operatingSystemName) {
    return SqliteCoordinationControlProtocol.isWindows(operatingSystemName)
        ? WINDOWS_LONG_PATH_VFS
        : null;
  }
}
