package dev.erst.fingrind.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Delegates native allocation while making one close failure deterministic for ownership tests. */
final class WindowsPrivateOutputFileCloseFailingArena implements Arena {
  private final Arena delegate;
  private final boolean failOnClose;
  private boolean closed;

  WindowsPrivateOutputFileCloseFailingArena(Arena delegate, boolean failOnClose) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.failOnClose = failOnClose;
  }

  @Override
  public MemorySegment allocate(long byteSize, long byteAlignment) {
    return delegate.allocate(byteSize, byteAlignment);
  }

  @Override
  public MemorySegment.Scope scope() {
    return delegate.scope();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    delegate.close();
    if (failOnClose) {
      throw new IllegalStateException("simulated arena close failure");
    }
  }
}
