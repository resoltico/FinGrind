package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;

/** Typed FFM view of {@code sqlite3_backup_init}. */
@FunctionalInterface
public interface SqliteNativeBackupInitCall {
  /** Invokes {@code sqlite3_backup_init}. */
  MemorySegment invoke(
      MemorySegment destinationDatabase,
      MemorySegment destinationSchemaName,
      MemorySegment sourceDatabase,
      MemorySegment sourceSchemaName);
}
