package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Exercises durable private-output name publication independently from a host operating system. */
class PrivateOutputDirectoryDurabilityTest {
  @TempDir Path temporaryDirectory;

  @Test
  void forcePreservesEveryNativeOutcomeAndReleasesTheHandle() throws Exception {
    assertDoesNotThrow(() -> PrivateOutputDirectoryDurability.force(temporaryDirectory));

    AtomicReference<PrivateOutputDirectoryDurability.OperatingSystem> requestedPlatform =
        new AtomicReference<>();
    RecordingBinding successfulBinding = new RecordingBinding(false, 0, null, 0, null);
    PrivateOutputDirectoryDurability.force(
        temporaryDirectory,
        PrivateOutputDirectoryDurability.OperatingSystem.WINDOWS,
        platform -> {
          requestedPlatform.set(platform);
          return successfulBinding;
        });
    assertEquals(PrivateOutputDirectoryDurability.OperatingSystem.WINDOWS, requestedPlatform.get());
    assertTrue(successfulBinding.handleReleased());
    assertEquals(1, successfulBinding.closeCalls());

    RecordingBinding invalidHandle = new RecordingBinding(true, 0, null, 0, null);
    assertDurabilityFailure(
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputDirectoryDurability.force(
                    temporaryDirectory,
                    PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> invalidHandle)));
    assertTrue(invalidHandle.handleReleased());
    assertEquals(0, invalidHandle.closeCalls());

    RecordingBinding flushReturnFailure = new RecordingBinding(false, 1, null, 0, null);
    assertDurabilityFailure(
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputDirectoryDurability.force(
                    temporaryDirectory,
                    PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> flushReturnFailure)));
    assertTrue(flushReturnFailure.handleReleased());

    IOException flushFailure = new IOException("flush failed");
    IOException closeFailure = new IOException("close failed");
    RecordingBinding failingFlushAndClose =
        new RecordingBinding(false, 0, flushFailure, 0, closeFailure);
    IOException retainedFailure =
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputDirectoryDurability.force(
                    temporaryDirectory,
                    PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> failingFlushAndClose));
    assertSame(flushFailure, retainedFailure);
    assertEquals(1, retainedFailure.getSuppressed().length);
    assertSame(closeFailure, retainedFailure.getSuppressed()[0]);
    assertTrue(failingFlushAndClose.handleReleased());

    RecordingBinding closeReturnFailure = new RecordingBinding(false, 0, null, 1, null);
    assertDurabilityFailure(
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputDirectoryDurability.force(
                    temporaryDirectory,
                    PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> closeReturnFailure)));
    assertTrue(closeReturnFailure.handleReleased());

    IllegalStateException runtimeFailure = new IllegalStateException("runtime flush failure");
    AtomicReference<Boolean> nativeCloseAttempted = new AtomicReference<>(false);
    assertSame(
        runtimeFailure,
        assertThrows(
            IllegalStateException.class,
            () ->
                PrivateOutputDirectoryDurability.force(
                    temporaryDirectory,
                    PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
                    ignored ->
                        new PrivateOutputDirectoryDurability.PlatformBinding() {
                          @Override
                          public PrivateOutputDirectoryDurability.DirectoryHandle open(
                              Path directory) {
                            return new RecordingHandle();
                          }

                          @Override
                          public boolean isInvalid(
                              PrivateOutputDirectoryDurability.DirectoryHandle handle) {
                            return false;
                          }

                          @Override
                          public int flush(
                              PrivateOutputDirectoryDurability.DirectoryHandle handle) {
                            throw runtimeFailure;
                          }

                          @Override
                          public int close(
                              PrivateOutputDirectoryDurability.DirectoryHandle handle) {
                            nativeCloseAttempted.set(true);
                            return 0;
                          }
                        })));
    assertTrue(nativeCloseAttempted.get());
  }

  @Test
  void identifiesTheSupportedPlatformsAndEnforcesNativeAccess() throws Exception {
    assertEquals(
        PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
        PrivateOutputDirectoryDurability.operatingSystem("Mac OS X"));
    assertEquals(
        PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
        PrivateOutputDirectoryDurability.operatingSystem("Linux"));
    assertEquals(
        PrivateOutputDirectoryDurability.OperatingSystem.POSIX,
        PrivateOutputDirectoryDurability.operatingSystem("Darwin"));
    assertEquals(
        PrivateOutputDirectoryDurability.OperatingSystem.WINDOWS,
        PrivateOutputDirectoryDurability.operatingSystem("Windows 11"));
    assertEquals(
        "Private-output directory durability is supported only on macOS, Linux, and Windows. Detected: Solaris",
        assertThrows(
                IOException.class,
                () -> PrivateOutputDirectoryDurability.operatingSystem("Solaris"))
            .getMessage());

    Module nativeAccessDisabled = ModuleLayer.boot().findModule("java.sql").orElseThrow();
    assertFalse(nativeAccessDisabled.isNativeAccessEnabled());
    assertEquals(
        "Private-output directory durability requires JVM native access. Rerun with --enable-native-access=java.sql.",
        assertThrows(
                IOException.class,
                () -> PrivateOutputDirectoryDurability.requireNativeAccess(nativeAccessDisabled))
            .getMessage());
    assertEquals(
        "java.sql", PrivateOutputDirectoryDurability.nativeAccessTarget(nativeAccessDisabled));
    assertEquals(
        "ALL-UNNAMED", PrivateOutputDirectoryDurability.nativeAccessTarget(getClass().getModule()));
    assertDoesNotThrow(
        () -> PrivateOutputDirectoryDurability.requireNativeAccess(getClass().getModule()));
  }

  @Test
  void describesBothNativeAbisWithoutExecutingTheOtherPlatform() {
    assertEquals(
        PrivateOutputDirectoryPlatformSpec.POSIX,
        PrivateOutputDirectoryPlatformSpec.forOperatingSystem(
            PrivateOutputDirectoryDurability.OperatingSystem.POSIX));
    assertEquals(
        PrivateOutputDirectoryPlatformSpec.WINDOWS,
        PrivateOutputDirectoryPlatformSpec.forOperatingSystem(
            PrivateOutputDirectoryDurability.OperatingSystem.WINDOWS));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment posixPath =
          PrivateOutputDirectoryPlatformSpec.POSIX.nativePath(arena, temporaryDirectory);
      MemorySegment windowsPath =
          PrivateOutputDirectoryPlatformSpec.WINDOWS.nativePath(arena, temporaryDirectory);
      assertEquals(2, PrivateOutputDirectoryPlatformSpec.POSIX.openArguments(posixPath).length);
      assertEquals(7, PrivateOutputDirectoryPlatformSpec.WINDOWS.openArguments(windowsPath).length);
      assertEquals(0, windowsPath.get(ValueLayout.JAVA_BYTE, windowsPath.byteSize() - 1));
      assertEquals(0, windowsPath.get(ValueLayout.JAVA_BYTE, windowsPath.byteSize() - 2));
      assertTrue(PrivateOutputDirectoryPlatformSpec.POSIX.isInvalid(-1));
      assertFalse(PrivateOutputDirectoryPlatformSpec.POSIX.isInvalid(0));
      assertTrue(PrivateOutputDirectoryPlatformSpec.WINDOWS.isInvalid(MemorySegment.ofAddress(-1)));
      assertFalse(PrivateOutputDirectoryPlatformSpec.WINDOWS.isInvalid(MemorySegment.NULL));
      assertEquals(0, PrivateOutputDirectoryPlatformSpec.POSIX.normalizeCompletionStatus(0));
      assertEquals(-1, PrivateOutputDirectoryPlatformSpec.POSIX.normalizeCompletionStatus(-1));
      assertEquals(1, PrivateOutputDirectoryPlatformSpec.WINDOWS.normalizeCompletionStatus(0));
      assertEquals(0, PrivateOutputDirectoryPlatformSpec.WINDOWS.normalizeCompletionStatus(1));
      assertEquals(1, PrivateOutputDirectoryPlatformSpec.POSIX.handleArguments(0).length);
      assertEquals(
          1, PrivateOutputDirectoryPlatformSpec.WINDOWS.handleArguments(MemorySegment.NULL).length);
    }
  }

  @Test
  void derivesTheExtendedLengthWindowsNativePath() {
    assertEquals(
        "\\\\?\\C:\\work\\Rīga büro\\attestation",
        PrivateOutputDirectoryPlatformSpec.extendedLengthWindowsPath(
            "C:\\work\\Rīga büro\\attestation"));
    assertEquals(
        "\\\\?\\UNC\\server\\share\\Rīga büro\\attestation",
        PrivateOutputDirectoryPlatformSpec.extendedLengthWindowsPath(
            "\\\\server\\share\\Rīga büro\\attestation"));
    assertEquals(
        "\\\\?\\C:\\work\\Rīga büro\\attestation",
        PrivateOutputDirectoryPlatformSpec.extendedLengthWindowsPath(
            "\\\\?\\C:\\work\\Rīga büro\\attestation"));
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void flushesADeepWindowsDirectoryThroughTheNativeTransport() throws Exception {
    Path deepDirectory = temporaryDirectory;
    while (deepDirectory.toString().length() <= 300) {
      deepDirectory = deepDirectory.resolve("Rīga büro attestation durability");
    }
    Files.createDirectories(deepDirectory);
    Path nativeDirectory = deepDirectory;

    assertDoesNotThrow(() -> PrivateOutputDirectoryDurability.force(nativeDirectory));
  }

  @Test
  void bindsTheCurrentPlatformAndValidatesTheForeignPlatformDeclaration() throws Exception {
    PrivateOutputDirectoryFfmTransport.NativeCallBinder binder =
        PrivateOutputDirectoryFfmTransport.nativeCallBinder();
    PrivateOutputDirectoryDurability.OperatingSystem operatingSystem =
        PrivateOutputDirectoryDurability.operatingSystem(System.getProperty("os.name", ""));
    PrivateOutputDirectoryPlatformSpec currentSpecification =
        PrivateOutputDirectoryPlatformSpec.forOperatingSystem(operatingSystem);
    SymbolLookup currentLookup =
        currentSpecification.usesDefaultLookup()
            ? Linker.nativeLinker().defaultLookup()
            : PrivateOutputDirectoryFfmTransport.libraryLookup(currentSpecification.libraryName());
    PrivateOutputDirectoryDurability.PlatformBinding current =
        binder.bind(currentSpecification, currentLookup);
    try (PrivateOutputDirectoryDurability.DirectoryHandle handle =
        current.open(temporaryDirectory)) {
      assertFalse(current.isInvalid(handle));
      assertEquals(0, current.flush(handle));
      assertEquals(0, current.close(handle));
    }

    PrivateOutputDirectoryPlatformSpec foreignSpecification =
        currentSpecification == PrivateOutputDirectoryPlatformSpec.POSIX
            ? PrivateOutputDirectoryPlatformSpec.WINDOWS
            : PrivateOutputDirectoryPlatformSpec.POSIX;
    SymbolLookup aliases =
        symbol ->
            currentLookup.find(
                switch (symbol) {
                  case "CreateFileW" -> currentSpecification.openSymbol();
                  case "FlushFileBuffers" -> currentSpecification.flushSymbol();
                  case "CloseHandle" -> currentSpecification.closeSymbol();
                  case "open" -> currentSpecification.openSymbol();
                  case "fsync" -> currentSpecification.flushSymbol();
                  case "close" -> currentSpecification.closeSymbol();
                  default -> symbol;
                });
    assertNotNull(binder.bind(foreignSpecification, aliases));

    IOException missingSymbol =
        assertThrows(
            IOException.class,
            () ->
                binder.bind(PrivateOutputDirectoryPlatformSpec.POSIX, ignored -> Optional.empty()));
    assertDurabilityFailure(missingSymbol);
    assertTrue(missingSymbol.getCause() instanceof IllegalStateException);
  }

  @Test
  void resolvesOnlyTheCurrentNativeLibraryAndKeepsForeignTransportTestable() throws Exception {
    SymbolLookup loadedLibrary =
        PrivateOutputDirectoryFfmTransport.libraryLookup(currentPlatformLibraryName());
    assertTrue(loadedLibrary.find(currentPlatformProbeSymbol()).isPresent());
    IOException missingLibrary =
        assertThrows(
            IOException.class,
            () ->
                PrivateOutputDirectoryFfmTransport.libraryLookup(
                    "fingrind-library-does-not-exist"));
    assertDurabilityFailure(missingLibrary);

    AtomicReference<PrivateOutputDirectoryPlatformSpec> boundSpecification =
        new AtomicReference<>();
    AtomicReference<String> loadedLibraryName = new AtomicReference<>();
    RecordingBinding expectedBinding = new RecordingBinding(false, 0, null, 0, null);
    PrivateOutputDirectoryFfmTransport.FfmDirectoryOperations operations =
        new PrivateOutputDirectoryFfmTransport.FfmDirectoryOperations(
            (specification, ignored) -> {
              boundSpecification.set(specification);
              return expectedBinding;
            },
            libraryName -> {
              loadedLibraryName.set(libraryName);
              return Linker.nativeLinker().defaultLookup();
            });
    assertSame(
        expectedBinding,
        operations.binding(PrivateOutputDirectoryDurability.OperatingSystem.POSIX));
    assertEquals(PrivateOutputDirectoryPlatformSpec.POSIX, boundSpecification.get());
    assertSame(
        expectedBinding,
        operations.binding(PrivateOutputDirectoryDurability.OperatingSystem.WINDOWS));
    assertEquals(PrivateOutputDirectoryPlatformSpec.WINDOWS, boundSpecification.get());
    assertEquals("kernel32", loadedLibraryName.get());
  }

  @Test
  void foreignTransportPreservesRuntimeErrorsAndWrapsCheckedFailures() throws Exception {
    MethodHandle checkedFailure =
        MethodHandles.dropArguments(
            MethodHandles.throwException(int.class, IOException.class)
                .bindTo(new IOException("checked failure")),
            0,
            MemorySegment.class,
            int.class);
    PrivateOutputDirectoryFfmTransport.FfmPlatformBinding checkedBinding =
        new PrivateOutputDirectoryFfmTransport.FfmPlatformBinding(
            PrivateOutputDirectoryPlatformSpec.POSIX,
            checkedFailure,
            checkedFailure,
            checkedFailure);
    IOException checkedException =
        assertThrows(IOException.class, () -> checkedBinding.open(temporaryDirectory));
    assertDurabilityFailure(checkedException);
    assertTrue(checkedException.getCause() instanceof Exception);

    MethodHandle runtimeFailure =
        MethodHandles.dropArguments(
            MethodHandles.throwException(int.class, IllegalStateException.class)
                .bindTo(new IllegalStateException("runtime failure")),
            0,
            MemorySegment.class,
            int.class);
    PrivateOutputDirectoryFfmTransport.FfmPlatformBinding runtimeBinding =
        new PrivateOutputDirectoryFfmTransport.FfmPlatformBinding(
            PrivateOutputDirectoryPlatformSpec.POSIX,
            runtimeFailure,
            runtimeFailure,
            runtimeFailure);
    assertEquals(
        "runtime failure",
        assertThrows(IllegalStateException.class, () -> runtimeBinding.open(temporaryDirectory))
            .getMessage());
    assertEquals(
        "Native directory binding received an incompatible handle.",
        assertThrows(
                IllegalArgumentException.class,
                () -> runtimeBinding.isInvalid(RecordingHandle.INSTANCE))
            .getMessage());
  }

  private static String currentPlatformLibraryName() {
    String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (operatingSystem.contains("windows")) {
      return "kernel32";
    }
    if (operatingSystem.contains("linux")) {
      return "libc.so.6";
    }
    if (operatingSystem.contains("mac") || operatingSystem.contains("darwin")) {
      return "libSystem.B.dylib";
    }
    throw new AssertionError("Unsupported test platform: " + operatingSystem);
  }

  private static String currentPlatformProbeSymbol() {
    return "kernel32".equals(currentPlatformLibraryName()) ? "CreateFileW" : "open";
  }

  private static void assertDurabilityFailure(IOException exception) {
    assertEquals("Failed to make the private-output directory durable.", exception.getMessage());
  }

  /** Simulates a native platform binding and records both native close and resource release. */
  private static final class RecordingBinding
      implements PrivateOutputDirectoryDurability.PlatformBinding {
    private final boolean invalid;
    private final int flushResult;
    private final @Nullable IOException flushFailure;
    private final int closeResult;
    private final @Nullable IOException closeFailure;
    private final RecordingHandle handle = new RecordingHandle();
    private int closeCalls;

    RecordingBinding(
        boolean invalid,
        int flushResult,
        @Nullable IOException flushFailure,
        int closeResult,
        @Nullable IOException closeFailure) {
      this.invalid = invalid;
      this.flushResult = flushResult;
      this.flushFailure = flushFailure;
      this.closeResult = closeResult;
      this.closeFailure = closeFailure;
    }

    @Override
    public PrivateOutputDirectoryDurability.DirectoryHandle open(Path directory) {
      return handle;
    }

    @Override
    public boolean isInvalid(PrivateOutputDirectoryDurability.DirectoryHandle ignored) {
      return invalid;
    }

    @Override
    public int flush(PrivateOutputDirectoryDurability.DirectoryHandle ignored) throws IOException {
      if (flushFailure != null) {
        throw flushFailure;
      }
      return flushResult;
    }

    @Override
    public int close(PrivateOutputDirectoryDurability.DirectoryHandle ignored) throws IOException {
      closeCalls++;
      if (closeFailure != null) {
        throw closeFailure;
      }
      return closeResult;
    }

    boolean handleReleased() {
      return handle.released;
    }

    int closeCalls() {
      return closeCalls;
    }
  }

  /** Records release of the test handle after the durability operation completes. */
  private static final class RecordingHandle
      implements PrivateOutputDirectoryDurability.DirectoryHandle {
    private static final RecordingHandle INSTANCE = new RecordingHandle();
    private boolean released;

    @Override
    public void close() {
      released = true;
    }
  }
}
