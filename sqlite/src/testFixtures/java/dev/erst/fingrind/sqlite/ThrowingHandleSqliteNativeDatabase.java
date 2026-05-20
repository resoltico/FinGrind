package dev.erst.fingrind.sqlite;

import java.lang.foreign.MemorySegment;

/** SQLite-owned native-database double whose handle lookup deterministically throws. */
public final class ThrowingHandleSqliteNativeDatabase extends SqliteNativeDatabase {
  private final SqliteNativeException failure;

  /** Creates one native-database double that throws the supplied handle failure on access. */
  public ThrowingHandleSqliteNativeDatabase(
      SqliteNativeApi sqliteApi, SqliteNativeException failure) {
    super(MemorySegment.NULL, sqliteApi);
    this.failure = failure;
  }

  @Override
  MemorySegment handle() {
    throw failure;
  }

  @Override
  public void close() {
    // This deterministic test double never owns one native handle.
  }
}
