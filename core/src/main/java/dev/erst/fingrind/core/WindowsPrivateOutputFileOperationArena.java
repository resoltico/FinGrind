package dev.erst.fingrind.core;

import java.lang.foreign.Arena;
import java.util.Objects;

/** Owns one non-null short-lived native arena for a retained Windows file operation. */
final class WindowsPrivateOutputFileOperationArena implements AutoCloseable {
  private final Arena arena;

  WindowsPrivateOutputFileOperationArena(Arena arena) {
    this.arena = Objects.requireNonNull(arena, "arena");
  }

  /** Returns the arena while this operation owner remains open. */
  Arena arena() {
    return arena;
  }

  @Override
  public void close() {
    arena.close();
  }
}
