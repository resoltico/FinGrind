package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;

/** Address- and wide-value-returning SQLite native-call interfaces used by the FFM bridge. */
public interface SqliteNativeAddressCalls {
  /** Functional view of one {@code (address, int) -> long} native call. */
  @FunctionalInterface
  public interface AddressIntToLongCall {
    /** Invokes the adapted native call. */
    long invoke(MemorySegment value, int intValue);
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
}
