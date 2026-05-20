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
  private @Nullable Throwable recordedFailure;

  SqliteNativeStatement(SqliteNativeDatabase database, String sql) {
    this.database = Objects.requireNonNull(database, "database");
    this.arena = Arena.ofConfined();
    try {
      MemorySegment statementPointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment sqlPointer = arena.allocateFrom(sql);
      SqliteNativeStatements.prepareStatement(
          database.handle(), sqlPointer, statementPointer, database.sqliteApi());
      this.statementHandle = statementPointer.get(ValueLayout.ADDRESS, 0);
    } catch (RuntimeException | Error exception) {
      arena.close();
      throw exception;
    }
  }

  void bindText(int parameterIndex, @Nullable String value) {
    try {
      if (value == null) {
        SqliteNativeStatements.bindNull(statementHandle, parameterIndex, database.sqliteApi());
        return;
      }
      MemorySegment valuePointer = arena.allocateFrom(value);
      SqliteNativeStatements.bindText(
          statementHandle,
          parameterIndex,
          valuePointer,
          utf8ByteLength(valuePointer),
          database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  void bindInt(int parameterIndex, int value) {
    try {
      SqliteNativeStatements.bindInt(statementHandle, parameterIndex, value, database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  void bindLong(int parameterIndex, long value) {
    try {
      SqliteNativeStatements.bindLong(statementHandle, parameterIndex, value, database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  int step() {
    try {
      return SqliteNativeStatements.step(statementHandle, database.handle(), database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  @Nullable String columnText(int columnIndex) {
    try {
      return SqliteNativeStatements.columnText(statementHandle, columnIndex, database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  int columnInt(int columnIndex) {
    try {
      return SqliteNativeStatements.columnInt(statementHandle, columnIndex, database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  long columnLong(int columnIndex) {
    try {
      return SqliteNativeStatements.columnLong(statementHandle, columnIndex, database.sqliteApi());
    } catch (RuntimeException | Error exception) {
      recordFailure(exception);
      throw exception;
    }
  }

  MemorySegment handle() {
    return statementHandle;
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
    RuntimeException finalizeRuntimeFailure = null;
    Error finalizeErrorFailure = null;
    try (arena) {
      SqliteNativeStatements.finalizeStatement(statementHandle, database.sqliteApi());
    } catch (RuntimeException exception) {
      if (recordedFailure == null) {
        finalizeRuntimeFailure = exception;
      } else {
        recordedFailure.addSuppressed(exception);
      }
    } catch (Error error) {
      if (recordedFailure == null) {
        finalizeErrorFailure = error;
      } else {
        recordedFailure.addSuppressed(error);
      }
    } finally {
      closed = true;
    }
    if (finalizeRuntimeFailure != null) {
      throw finalizeRuntimeFailure;
    }
    if (finalizeErrorFailure != null) {
      throw finalizeErrorFailure;
    }
  }

  private void recordFailure(Throwable failure) {
    if (recordedFailure == null) {
      recordedFailure = failure;
    }
  }
}
