package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.util.Objects;

/** Groups the raw Win32 symbol tables used by the protected-output capability. */
record WindowsPrivateOutputFileBindings(
    WindowsPrivateOutputFileHandleBindings fileBindings,
    WindowsPrivateOutputFileOwnerBindings ownerBindings,
    WindowsPrivateOutputFileSecurityBindings securityBindings) {
  WindowsPrivateOutputFileBindings {
    Objects.requireNonNull(fileBindings, "fileBindings");
    Objects.requireNonNull(ownerBindings, "ownerBindings");
    Objects.requireNonNull(securityBindings, "securityBindings");
  }

  /** Binds the vocabulary from one library resolver and ABI binder. */
  static WindowsPrivateOutputFileBindings bind(
      LibraryLookup libraryLookup, WindowsPrivateOutputFileBindingSupport.Binder binder)
      throws IOException {
    LibraryLookup checkedLookup = Objects.requireNonNull(libraryLookup, "libraryLookup");
    SymbolLookup kernel32 = lookupLibrary(checkedLookup, "kernel32");
    SymbolLookup advapi32 = lookupLibrary(checkedLookup, "advapi32");
    return new WindowsPrivateOutputFileBindings(
        WindowsPrivateOutputFileHandleBindings.bind(kernel32, binder),
        WindowsPrivateOutputFileOwnerBindings.bind(kernel32, advapi32, binder),
        WindowsPrivateOutputFileSecurityBindings.bind(advapi32, binder));
  }

  /** Resolves one Windows native library for the complete protected-output binding vocabulary. */
  @FunctionalInterface
  interface LibraryLookup {
    /**
     * Resolves one named Windows library within the supplied arena's lifetime.
     *
     * @param libraryName native library name
     * @param arena lookup lifetime owner
     * @return the native symbol lookup
     * @throws IOException if resolution fails
     */
    SymbolLookup lookup(String libraryName, Arena arena) throws IOException;
  }

  private static SymbolLookup lookupLibrary(LibraryLookup libraryLookup, String libraryName)
      throws IOException {
    try {
      return libraryLookup.lookup(libraryName, Arena.global());
    } catch (IllegalArgumentException exception) {
      throw new IOException(
          "FinGrind could not load Windows native library " + libraryName + ".", exception);
    }
  }
}
