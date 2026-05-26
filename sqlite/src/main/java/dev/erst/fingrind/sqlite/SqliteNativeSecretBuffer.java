package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Native secret owner that zeroizes one copied passphrase buffer before the arena is released. */
final class SqliteNativeSecretBuffer implements AutoCloseable {
  private final MemorySegment buffer;
  private boolean closed;

  private SqliteNativeSecretBuffer(MemorySegment buffer) {
    this.buffer = Objects.requireNonNull(buffer, "buffer");
  }

  static SqliteNativeSecretBuffer cString(SqliteBookPassphrase bookPassphrase, Arena arena) {
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(arena, "arena");
    return new SqliteNativeSecretBuffer(bookPassphrase.copyToCString(arena));
  }

  MemorySegment pointer() {
    return buffer;
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    buffer.fill((byte) 0);
    closed = true;
  }
}
