package dev.erst.fingrind.sqlite.internal;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.util.Objects;

/** Typed adapters over raw SQLite method handles so callers avoid raw bridge signatures. */
public final class SqliteNativeCalls {
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

  private SqliteNativeCalls() {}

  /** Adapts one raw method handle to the typed {@code sqlite3_open_v2} view. */
  public static OpenV2Call openV2(MethodHandle handle) {
    return adapt(OpenV2Call.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address) -> int} view. */
  public static AddressToIntCall addressToInt(MethodHandle handle) {
    return adapt(AddressToIntCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, int) -> int} view. */
  public static AddressIntToIntCall addressIntToInt(MethodHandle handle) {
    return adapt(AddressIntToIntCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, int) -> long} view. */
  public static AddressIntToLongCall addressIntToLong(MethodHandle handle) {
    return adapt(AddressIntToLongCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, int, long) -> int} view. */
  public static AddressIntLongToIntCall addressIntLongToInt(MethodHandle handle) {
    return adapt(AddressIntLongToIntCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, address, int) -> int} view. */
  public static AddressAddressIntToIntCall addressAddressIntToInt(MethodHandle handle) {
    return adapt(AddressAddressIntToIntCall.class, handle);
  }

  /**
   * Adapts one raw method handle to the typed {@code (address, address, address, int) -> int} view.
   */
  public static AddressAddressAddressIntToIntCall addressAddressAddressIntToInt(
      MethodHandle handle) {
    return adapt(AddressAddressAddressIntToIntCall.class, handle);
  }

  /**
   * Adapts one raw method handle to the typed {@code (address, address, int, address) -> int} view.
   */
  public static AddressAddressIntAddressToIntCall addressAddressIntAddressToInt(
      MethodHandle handle) {
    return adapt(AddressAddressIntAddressToIntCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code sqlite3_exec} view. */
  public static ExecCall exec(MethodHandle handle) {
    return adapt(ExecCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code sqlite3_prepare_v2} view. */
  public static PrepareV2Call prepareV2(MethodHandle handle) {
    return adapt(PrepareV2Call.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code sqlite3_bind_text} view. */
  public static BindTextCall bindText(MethodHandle handle) {
    return adapt(BindTextCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, int) -> address} view. */
  public static AddressIntToAddressCall addressIntToAddress(MethodHandle handle) {
    return adapt(AddressIntToAddressCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address) -> address} view. */
  public static AddressToAddressCall addressToAddress(MethodHandle handle) {
    return adapt(AddressToAddressCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (int) -> address} view. */
  public static IntToAddressCall intToAddress(MethodHandle handle) {
    return adapt(IntToAddressCall.class, handle);
  }

  /** Adapts one raw method handle to the typed no-argument address-returning view. */
  public static NoArgAddressCall noArgAddress(MethodHandle handle) {
    return adapt(NoArgAddressCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address) -> long} view. */
  public static AddressToLongCall addressToLong(MethodHandle handle) {
    return adapt(AddressToLongCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address) -> void} view. */
  public static AddressToVoidCall addressToVoid(MethodHandle handle) {
    return adapt(AddressToVoidCall.class, handle);
  }

  /** Adapts one raw method handle to the typed no-argument integer-returning view. */
  public static NoArgIntCall noArgInt(MethodHandle handle) {
    return adapt(NoArgIntCall.class, handle);
  }

  /** Adapts one raw method handle to the typed {@code (address, int, int) -> int} view. */
  public static AddressIntIntToIntCall addressIntIntToInt(MethodHandle handle) {
    return adapt(AddressIntIntToIntCall.class, handle);
  }

  private static <T> T adapt(Class<T> interfaceType, MethodHandle handle) {
    Objects.requireNonNull(interfaceType, "interfaceType");
    Objects.requireNonNull(handle, "handle");
    return interfaceType.cast(MethodHandleProxies.asInterfaceInstance(interfaceType, handle));
  }
}
