package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;

/** Typed SQLite native-call interfaces used by the FFM bridge. */
public interface SqliteNativeCalls {
  /** Functional view of {@code sqlite3_open_v2}. */
  @FunctionalInterface
  public interface OpenV2Call {
    /** Invokes the adapted open call. */
    int invoke(
        MemorySegment filename, MemorySegment databasePointer, int openFlags, MemorySegment vfs);
  }

  /** Functional view of one {@code (address) -> int} native call. */
  @FunctionalInterface
  public interface AddressToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value);
  }

  /** Functional view of one {@code (address, int) -> int} native call. */
  @FunctionalInterface
  public interface AddressIntToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, int intValue);
  }

  /** Functional view of one {@code (address, int) -> long} native call. */
  @FunctionalInterface
  public interface AddressIntToLongCall {
    /** Invokes the adapted native call. */
    long invoke(MemorySegment value, int intValue);
  }

  /** Functional view of one {@code (address, int, long) -> int} native call. */
  @FunctionalInterface
  public interface AddressIntLongToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, int intValue, long longValue);
  }

  /** Functional view of one {@code (address, address, int) -> int} native call. */
  @FunctionalInterface
  public interface AddressAddressIntToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, MemorySegment bytes, int byteLength);
  }

  /** Functional view of one {@code (address, address, address, int) -> int} native call. */
  @FunctionalInterface
  public interface AddressAddressAddressIntToIntCall {
    /** Invokes the adapted native call. */
    int invoke(
        MemorySegment firstValue, MemorySegment secondValue, MemorySegment thirdValue, int value);
  }

  /** Functional view of one {@code (address, address, int, address) -> int} native call. */
  @FunctionalInterface
  public interface AddressAddressIntAddressToIntCall {
    /** Invokes the adapted native call. */
    int invoke(
        MemorySegment firstValue, MemorySegment secondValue, int intValue, MemorySegment pointer);
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

  /** Functional view of one {@code (address, int) -> address} native call. */
  @FunctionalInterface
  public interface AddressIntToAddressCall {
    /** Invokes the adapted native call. */
    MemorySegment invoke(MemorySegment value, int intValue);
  }

  /** Functional view of one {@code (address) -> address} native call. */
  @FunctionalInterface
  public interface AddressToAddressCall {
    /** Invokes the adapted native call. */
    MemorySegment invoke(MemorySegment value);
  }

  /** Functional view of one {@code (int) -> address} native call. */
  @FunctionalInterface
  public interface IntToAddressCall {
    /** Invokes the adapted native call. */
    MemorySegment invoke(int value);
  }

  /** Functional view of one no-argument address-returning native call. */
  @FunctionalInterface
  public interface NoArgAddressCall {
    /** Invokes the adapted native call. */
    MemorySegment invoke();
  }

  /** Functional view of one {@code (address) -> long} native call. */
  @FunctionalInterface
  public interface AddressToLongCall {
    /** Invokes the adapted native call. */
    long invoke(MemorySegment value);
  }

  /** Functional view of one {@code (address) -> void} native call. */
  @FunctionalInterface
  public interface AddressToVoidCall {
    /** Invokes the adapted native call. */
    void invoke(MemorySegment value);
  }

  /** Functional view of one no-argument integer-returning native call. */
  @FunctionalInterface
  public interface NoArgIntCall {
    /** Invokes the adapted native call. */
    int invoke();
  }

  /** Functional view of one {@code (address, int, int) -> int} native call. */
  @FunctionalInterface
  public interface AddressIntIntToIntCall {
    /** Invokes the adapted native call. */
    int invoke(MemorySegment value, int left, int right);
  }
}
