package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Binds the attestation key-directory durability policy to platform-native FFM operations. */
final class AttestationDirectoryFfmTransport {
  private AttestationDirectoryFfmTransport() {}

  static AttestationDirectoryDurability.DirectoryDurabilityOperations production() {
    return FfmDirectoryOperations.production();
  }

  static SymbolLookup libraryLookup(String libraryName) throws IOException {
    try {
      return SymbolLookup.libraryLookup(
          Objects.requireNonNull(libraryName, "libraryName"), Arena.global());
    } catch (IllegalArgumentException exception) {
      throw AttestationDirectoryDurability.failure(exception);
    }
  }

  static NativeCallBinder nativeCallBinder() {
    return FfmPlatformBinding::bind;
  }

  /** Binds a platform declaration to its resolved native symbols. */
  @FunctionalInterface
  interface NativeCallBinder {
    /** Produces directory-handle operations from the platform declaration and symbol lookup. */
    AttestationDirectoryDurability.PlatformBinding bind(
        AttestationDirectoryPlatformSpec specification, SymbolLookup lookup) throws IOException;
  }

  /**
   * Resolves an explicitly named native library for the platform that requires one.
   *
   * @see SymbolLookup
   */
  @FunctionalInterface
  interface NativeLibraryLookup {
    /**
     * Resolves the supplied native library name for a one-time FFM binding attempt.
     *
     * @param libraryName native library name for the selected platform
     * @return lookup for symbols exported by the library
     * @throws IOException when the library cannot be resolved
     */
    SymbolLookup lookup(String libraryName) throws IOException;
  }

  /**
   * Makes a POSIX or Windows platform binding from resolved foreign function symbols.
   *
   * @see AttestationDirectoryPlatformSpec
   */
  static final class FfmPlatformBinding implements AttestationDirectoryDurability.PlatformBinding {
    private final AttestationDirectoryPlatformSpec specification;
    private final MethodHandle open;
    private final MethodHandle flush;
    private final MethodHandle close;

    FfmPlatformBinding(
        AttestationDirectoryPlatformSpec specification,
        MethodHandle open,
        MethodHandle flush,
        MethodHandle close) {
      this.specification = Objects.requireNonNull(specification, "specification");
      this.open = Objects.requireNonNull(open, "open");
      this.flush = Objects.requireNonNull(flush, "flush");
      this.close = Objects.requireNonNull(close, "close");
    }

    static AttestationDirectoryDurability.PlatformBinding bind(
        AttestationDirectoryPlatformSpec specification, SymbolLookup lookup) throws IOException {
      Objects.requireNonNull(specification, "specification");
      Objects.requireNonNull(lookup, "lookup");
      return new FfmPlatformBinding(
          specification,
          downcall(lookup, specification.openSymbol(), specification.openDescriptor()),
          downcall(lookup, specification.flushSymbol(), specification.flushDescriptor()),
          downcall(lookup, specification.closeSymbol(), specification.closeDescriptor()));
    }

    @Override
    public AttestationDirectoryDurability.DirectoryHandle open(Path directory) throws IOException {
      Arena arena = Arena.ofConfined();
      try {
        Object nativeHandle =
            invoke(
                open,
                specification.openArguments(
                    specification.nativePath(
                        arena, Objects.requireNonNull(directory, "directory"))));
        return new FfmDirectoryHandle(arena, nativeHandle);
      } catch (IOException | RuntimeException | Error exception) {
        arena.close();
        throw exception;
      }
    }

    @Override
    public boolean isInvalid(AttestationDirectoryDurability.DirectoryHandle handle) {
      return specification.isInvalid(requireFfmHandle(handle).nativeHandle());
    }

    @Override
    public int flush(AttestationDirectoryDurability.DirectoryHandle handle) throws IOException {
      int nativeStatus =
          (int)
              invoke(flush, specification.handleArguments(requireFfmHandle(handle).nativeHandle()));
      return specification.normalizeCompletionStatus(nativeStatus);
    }

    @Override
    public int close(AttestationDirectoryDurability.DirectoryHandle handle) throws IOException {
      int nativeStatus =
          (int)
              invoke(close, specification.handleArguments(requireFfmHandle(handle).nativeHandle()));
      return specification.normalizeCompletionStatus(nativeStatus);
    }

    private static MethodHandle downcall(
        SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) throws IOException {
      Optional<MemorySegment> address = lookup.find(symbol);
      if (address.isEmpty()) {
        throw AttestationDirectoryDurability.failure(
            new IllegalStateException("Missing native directory-sync symbol: " + symbol));
      }
      return Linker.nativeLinker().downcallHandle(address.orElseThrow(), descriptor);
    }

    private static Object invoke(MethodHandle function, Object[] arguments) throws IOException {
      try {
        return function.invokeWithArguments(arguments);
      } catch (RuntimeException | Error exception) {
        throw exception;
      } catch (Throwable exception) {
        throw AttestationDirectoryDurability.failure(exception);
      }
    }

    private static FfmDirectoryHandle requireFfmHandle(
        AttestationDirectoryDurability.DirectoryHandle handle) {
      if (!(handle instanceof FfmDirectoryHandle ffmHandle)) {
        throw new IllegalArgumentException(
            "Native directory binding received an incompatible handle.");
      }
      return ffmHandle;
    }
  }

  /* Keeps the confined arena that owns a foreign-memory directory path and handle alive. */
  record FfmDirectoryHandle(Arena arena, Object nativeHandle)
      implements AttestationDirectoryDurability.DirectoryHandle {
    FfmDirectoryHandle {
      Objects.requireNonNull(arena, "arena");
      Objects.requireNonNull(nativeHandle, "nativeHandle");
    }

    @Override
    public void close() {
      arena.close();
    }
  }

  /**
   * Resolves FFM bindings only for the current target platform.
   *
   * @see NativeCallBinder
   */
  static final class FfmDirectoryOperations
      implements AttestationDirectoryDurability.DirectoryDurabilityOperations {
    private final NativeCallBinder binder;
    private final NativeLibraryLookup libraryLookup;

    FfmDirectoryOperations(NativeCallBinder binder, NativeLibraryLookup libraryLookup) {
      this.binder = Objects.requireNonNull(binder, "binder");
      this.libraryLookup = Objects.requireNonNull(libraryLookup, "libraryLookup");
    }

    static FfmDirectoryOperations production() {
      return new FfmDirectoryOperations(
          nativeCallBinder(), AttestationDirectoryFfmTransport::libraryLookup);
    }

    @Override
    public AttestationDirectoryDurability.PlatformBinding binding(
        AttestationDirectoryDurability.OperatingSystem operatingSystem) throws IOException {
      AttestationDirectoryPlatformSpec specification =
          AttestationDirectoryPlatformSpec.forOperatingSystem(operatingSystem);
      SymbolLookup lookup =
          specification.usesDefaultLookup()
              ? Linker.nativeLinker().defaultLookup()
              : libraryLookup.lookup(specification.libraryName());
      return binder.bind(specification, lookup);
    }
  }
}
