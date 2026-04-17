package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Prepared SQLite statement with statement-scoped native memory for bound text values.
 *
 * <p>Statements are intentionally single-use in this adapter. Prepare, bind, step, and close one
 * statement instance instead of resetting and reusing it across operations.
 */
final class SqliteNativeStatement implements AutoCloseable {
  private final SqliteNativeDatabase database;
  private final Arena arena;
  private final MemorySegment statementHandle;

  private boolean closed;

  SqliteNativeStatement(SqliteNativeDatabase database, String sql) throws SqliteNativeException {
    this.database = Objects.requireNonNull(database, "database");
    this.arena = Arena.ofConfined();
    try {
      MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment sqlPointer = arena.allocateFrom(sql);
      SqliteNativeLibrary.prepareStatement(database.handle(), sqlPointer, statementPointer);
      this.statementHandle = statementPointer.get(ValueLayout.ADDRESS, 0);
    } catch (RuntimeException | Error throwable) {
      arena.close();
      throw throwable;
    } catch (SqliteNativeException exception) {
      arena.close();
      throw exception;
    }
  }

  void bindText(int parameterIndex, @Nullable String value) throws SqliteNativeException {
    if (value == null) {
      SqliteNativeLibrary.bindNull(statementHandle, parameterIndex);
      return;
    }
    MemorySegment valuePointer = arena.allocateFrom(value);
    SqliteNativeLibrary.bindText(
        statementHandle, parameterIndex, valuePointer, utf8ByteLength(valuePointer));
  }

  void bindInt(int parameterIndex, int value) throws SqliteNativeException {
    SqliteNativeLibrary.bindInt(statementHandle, parameterIndex, value);
  }

  int step() throws SqliteNativeException {
    return SqliteNativeLibrary.step(database.handle(), statementHandle);
  }

  @Nullable String columnText(int columnIndex) {
    return SqliteNativeLibrary.columnText(statementHandle, columnIndex);
  }

  int columnInt(int columnIndex) {
    return SqliteNativeLibrary.columnInt(statementHandle, columnIndex);
  }

  static int utf8ByteLength(MemorySegment valuePointer) {
    // Arena.allocateFrom(String) already encoded one null-terminated UTF-8 buffer for SQLite.
    return Math.toIntExact(valuePointer.byteSize() - 1L);
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    SqliteNativeLibrary.finalizeStatement(statementHandle);
    arena.close();
    closed = true;
  }
}
