package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;

/** Statement-oriented SQLite native-call interfaces used by the FFM bridge. */
public interface SqliteNativeStatementCalls {
  /** Functional view of one {@code (address, int, long) -> int} native call. */
  @FunctionalInterface
  public interface AddressIntLongToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, int intValue, long longValue);
  }

  /** Functional view of {@code sqlite3_exec}. */
  @FunctionalInterface
  public interface ExecCall {
    /** Invokes the adapted exec call. */
    int invoke(
        MemorySegment databaseHandle,
        MemorySegment sql,
        MemorySegment callback,
        MemorySegment callbackArgument,
        MemorySegment errorPointer);
  }

  /** Functional view of {@code sqlite3_prepare_v2}. */
  @FunctionalInterface
  public interface PrepareV2Call {
    /** Invokes the adapted prepare call. */
    int invoke(
        MemorySegment databaseHandle,
        MemorySegment sql,
        int byteLength,
        MemorySegment statementPointer,
        MemorySegment tailPointer);
  }

  /** Functional view of {@code sqlite3_bind_text}. */
  @FunctionalInterface
  public interface BindTextCall {
    /** Invokes the adapted bind-text call. */
    int invoke(
        MemorySegment statementHandle,
        int parameterIndex,
        MemorySegment text,
        int byteLength,
        MemorySegment destructor);
  }

  /** Functional view of one {@code (address, int, int) -> int} native call. */
  @FunctionalInterface
  public interface AddressIntIntToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, int left, int right);
  }
}
