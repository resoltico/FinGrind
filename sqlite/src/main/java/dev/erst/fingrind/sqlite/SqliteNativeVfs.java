package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
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

  /** Verifies that the selected platform VFS was compiled into the loaded SQLite runtime. */
  static void requireCurrentHostVfsAvailable(SymbolLookup lookup) {
    Objects.requireNonNull(lookup, "lookup");
    @Nullable String vfsName = openVfsName(System.getProperty("os.name", ""));
    if (vfsName == null) {
      return;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment vfsNamePointer = arena.allocateFrom(vfsName);
      MemorySegment registeredVfs =
          SqliteNativeInvocation.invoke(
              "Failed to inspect the SQLite native VFS registry.",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeCalls.AddressToAddressCall.class,
                          SqliteNativeApiBindings.downcall(
                              lookup,
                              "sqlite3_vfs_find",
                              FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                      .invoke(vfsNamePointer));
      requireRegistered(vfsName, registeredVfs);
    }
  }

  /** Rejects a native runtime that omitted the named VFS required by the selected platform. */
  static void requireRegistered(String vfsName, MemorySegment registeredVfs) {
    String checkedVfsName = Objects.requireNonNull(vfsName, "vfsName");
    if (checkedVfsName.isBlank()) {
      throw new IllegalArgumentException("vfsName must not be blank.");
    }
    if (Objects.requireNonNull(registeredVfs, "registeredVfs").equals(MemorySegment.NULL)) {
      throw new IllegalStateException(
          "The loaded SQLite runtime does not provide the required VFS '" + checkedVfsName + "'.");
    }
  }

  /** Returns the explicit VFS required to avoid SQLite's classic Windows path ceiling. */
  static @Nullable String openVfsName(String operatingSystemName) {
    return SqliteCoordinationControlProtocol.isWindows(operatingSystemName)
        ? WINDOWS_LONG_PATH_VFS
        : null;
  }
}
