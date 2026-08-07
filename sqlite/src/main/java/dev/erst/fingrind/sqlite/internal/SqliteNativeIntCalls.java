package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;

/** Integer-returning SQLite native-call interfaces used by the FFM bridge. */
public interface SqliteNativeIntCalls {
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

  /** Functional view of one no-argument integer-returning native call. */
  @FunctionalInterface
  public interface NoArgIntCall {
    /** Invokes the adapted native call. */
    int invoke();
  }
}
