package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/** Resolves and links the individual Win32 symbols used by protected-output call families. */
final class WindowsPrivateOutputFileBindingSupport {
  private WindowsPrivateOutputFileBindingSupport() {}

  static NativeRuntime nativeRuntime() {
    return new NativeRuntime(
        SymbolLookup::libraryLookup,
        nativeBinder(new FfmDowncallFactory(Linker.nativeLinker()), "GetLastError"));
  }

  static Binder nativeBinder(DowncallFactory downcallFactory, String captureStateName) {
    DowncallFactory checkedDowncallFactory =
        Objects.requireNonNull(downcallFactory, "downcallFactory");
    String checkedCaptureStateName = Objects.requireNonNull(captureStateName, "captureStateName");
    return new Binder() {
      @Override
      public MethodHandle captured(
          SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) throws IOException {
        return checkedDowncallFactory.capturedDowncall(
            requiredSymbol(lookup, symbol), descriptor, checkedCaptureStateName);
      }

      @Override
      public MethodHandle direct(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor)
          throws IOException {
        return checkedDowncallFactory.directDowncall(requiredSymbol(lookup, symbol), descriptor);
      }
    };
  }

  /** Builds typed direct and captured-last-error downcall handles from resolved Win32 symbols. */
  interface DowncallFactory {
    /**
     * Links a downcall that captures immediate thread-local last-error state.
     *
     * @param symbol resolved native symbol
     * @param descriptor native ABI descriptor
     * @param captureStateName JDK capture-state name
     * @return the linked downcall handle
     */
    MethodHandle capturedDowncall(
        MemorySegment symbol, FunctionDescriptor descriptor, String captureStateName);

    /**
     * Links a downcall whose native result reports its own error state.
     *
     * @param symbol resolved native symbol
     * @param descriptor native ABI descriptor
     * @return the linked downcall handle
     */
    MethodHandle directDowncall(MemorySegment symbol, FunctionDescriptor descriptor);
  }

  /** Production adapter from FinGrind's explicit calling conventions to the sealed JDK linker. */
  static final class FfmDowncallFactory implements DowncallFactory {
    private final Linker linker;

    FfmDowncallFactory(Linker linker) {
      this.linker = Objects.requireNonNull(linker, "linker");
    }

    @Override
    public MethodHandle capturedDowncall(
        MemorySegment symbol, FunctionDescriptor descriptor, String captureStateName) {
      return linker.downcallHandle(
          symbol, descriptor, Linker.Option.captureCallState(captureStateName));
    }

    @Override
    public MethodHandle directDowncall(MemorySegment symbol, FunctionDescriptor descriptor) {
      return linker.downcallHandle(symbol, descriptor);
    }
  }

  /**
   * Links one complete Win32 symbol vocabulary while keeping ABI construction independently
   * testable.
   *
   * @implNote Implementations must resolve every symbol from the supplied lookup without global
   *     mutable binding state.
   */
  interface Binder {
    /**
     * Links one Win32 call that captures the immediate thread-local last-error value.
     *
     * @param lookup native-library lookup
     * @param symbol native symbol name
     * @param descriptor native ABI descriptor
     * @return the linked downcall handle
     * @throws IOException if symbol resolution fails
     */
    MethodHandle captured(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor)
        throws IOException;

    /**
     * Links one Win32 call that returns an error code directly.
     *
     * @param lookup native-library lookup
     * @param symbol native symbol name
     * @param descriptor native ABI descriptor
     * @return the linked downcall handle
     * @throws IOException if symbol resolution fails
     */
    MethodHandle direct(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor)
        throws IOException;
  }

  /**
   * Supplies a complete, fresh Win32 call table for one protected output operation.
   *
   * @implNote The caller owns any native lookup resources captured by the table.
   */
  @FunctionalInterface
  interface CallTableSource {
    /**
     * Binds and returns the complete call table.
     *
     * @return the fresh call table
     * @throws IOException if binding fails
     */
    WindowsPrivateOutputFileCalls calls() throws IOException;
  }

  /**
   * Composes the production or test-native library and linker dependencies without global state.
   *
   * @implNote A runtime is reusable only while its supplied library lookups remain valid.
   */
  static final class NativeRuntime {
    private final WindowsPrivateOutputFileBindings.LibraryLookup libraryLookup;
    private final Binder binder;

    NativeRuntime(WindowsPrivateOutputFileBindings.LibraryLookup libraryLookup, Binder binder) {
      this.libraryLookup = Objects.requireNonNull(libraryLookup, "libraryLookup");
      this.binder = Objects.requireNonNull(binder, "binder");
    }

    WindowsPrivateOutputFileCalls calls() throws IOException {
      return WindowsPrivateOutputFileNative.calls(
          () ->
              WindowsPrivateOutputFileFfmCalls.fromBindings(
                  WindowsPrivateOutputFileBindings.bind(libraryLookup, binder)));
    }
  }

  private static MemorySegment requiredSymbol(SymbolLookup lookup, String symbol)
      throws IOException {
    return Objects.requireNonNull(lookup, "lookup")
        .find(Objects.requireNonNull(symbol, "symbol"))
        .orElseThrow(
            () ->
                new IOException(
                    "Windows private-output transport is missing native symbol %s."
                        .formatted(symbol)));
  }
}
