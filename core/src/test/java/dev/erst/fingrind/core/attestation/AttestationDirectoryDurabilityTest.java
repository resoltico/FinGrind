package dev.erst.fingrind.core.attestation;

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
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises durable attestation-key name publication independently from a host operating system.
 */
class AttestationDirectoryDurabilityTest {
  @TempDir Path temporaryDirectory;

  @Test
  void forcePreservesEveryNativeOutcomeAndReleasesTheHandle() throws Exception {
    assertDoesNotThrow(() -> AttestationDirectoryDurability.force(temporaryDirectory));

    AtomicReference<AttestationDirectoryDurability.OperatingSystem> requestedPlatform =
        new AtomicReference<>();
    RecordingBinding successfulBinding = new RecordingBinding(false, 0, null, 0, null);
    AttestationDirectoryDurability.force(
        temporaryDirectory,
        AttestationDirectoryDurability.OperatingSystem.WINDOWS,
        platform -> {
          requestedPlatform.set(platform);
          return successfulBinding;
        });
    assertEquals(AttestationDirectoryDurability.OperatingSystem.WINDOWS, requestedPlatform.get());
    assertTrue(successfulBinding.handleReleased());
    assertEquals(1, successfulBinding.closeCalls());

    RecordingBinding invalidHandle = new RecordingBinding(true, 0, null, 0, null);
    assertDurabilityFailure(
        assertThrows(
            IOException.class,
            () ->
                AttestationDirectoryDurability.force(
                    temporaryDirectory,
                    AttestationDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> invalidHandle)));
    assertTrue(invalidHandle.handleReleased());
    assertEquals(0, invalidHandle.closeCalls());

    RecordingBinding flushReturnFailure = new RecordingBinding(false, 1, null, 0, null);
    assertDurabilityFailure(
        assertThrows(
            IOException.class,
            () ->
                AttestationDirectoryDurability.force(
                    temporaryDirectory,
                    AttestationDirectoryDurability.OperatingSystem.POSIX,
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
                AttestationDirectoryDurability.force(
                    temporaryDirectory,
                    AttestationDirectoryDurability.OperatingSystem.POSIX,
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
                AttestationDirectoryDurability.force(
                    temporaryDirectory,
                    AttestationDirectoryDurability.OperatingSystem.POSIX,
                    ignored -> closeReturnFailure)));
    assertTrue(closeReturnFailure.handleReleased());

    IllegalStateException runtimeFailure = new IllegalStateException("runtime flush failure");
    AtomicReference<Boolean> nativeCloseAttempted = new AtomicReference<>(false);
    assertSame(
        runtimeFailure,
        assertThrows(
            IllegalStateException.class,
            () ->
                AttestationDirectoryDurability.force(
                    temporaryDirectory,
                    AttestationDirectoryDurability.OperatingSystem.POSIX,
                    ignored ->
                        new AttestationDirectoryDurability.PlatformBinding() {
                          @Override
                          public AttestationDirectoryDurability.DirectoryHandle open(
                              Path directory) {
                            return new RecordingHandle();
                          }

                          @Override
                          public boolean isInvalid(
                              AttestationDirectoryDurability.DirectoryHandle handle) {
                            return false;
                          }

                          @Override
                          public int flush(AttestationDirectoryDurability.DirectoryHandle handle) {
                            throw runtimeFailure;
                          }

                          @Override
                          public int close(AttestationDirectoryDurability.DirectoryHandle handle) {
                            nativeCloseAttempted.set(true);
                            return 0;
                          }
                        })));
    assertTrue(nativeCloseAttempted.get());
  }

  @Test
  void identifiesTheSupportedPlatformsAndEnforcesNativeAccess() throws Exception {
    assertEquals(
        AttestationDirectoryDurability.OperatingSystem.POSIX,
        AttestationDirectoryDurability.operatingSystem("Mac OS X"));
    assertEquals(
        AttestationDirectoryDurability.OperatingSystem.POSIX,
        AttestationDirectoryDurability.operatingSystem("Linux"));
    assertEquals(
        AttestationDirectoryDurability.OperatingSystem.POSIX,
        AttestationDirectoryDurability.operatingSystem("Darwin"));
    assertEquals(
        AttestationDirectoryDurability.OperatingSystem.WINDOWS,
        AttestationDirectoryDurability.operatingSystem("Windows 11"));
    assertEquals(
        "Attestation-controlled artifact directory durability is supported only on macOS, Linux, and Windows. Detected: Solaris",
        assertThrows(
                IOException.class, () -> AttestationDirectoryDurability.operatingSystem("Solaris"))
            .getMessage());

    Module nativeAccessDisabled = ModuleLayer.boot().findModule("java.sql").orElseThrow();
    assertFalse(nativeAccessDisabled.isNativeAccessEnabled());
    assertEquals(
        "Attestation-controlled artifact directory durability requires JVM native access. Rerun with --enable-native-access=java.sql.",
        assertThrows(
                IOException.class,
                () -> AttestationDirectoryDurability.requireNativeAccess(nativeAccessDisabled))
            .getMessage());
    assertEquals(
        "java.sql", AttestationDirectoryDurability.nativeAccessTarget(nativeAccessDisabled));
    assertEquals(
        "ALL-UNNAMED", AttestationDirectoryDurability.nativeAccessTarget(getClass().getModule()));
    assertDoesNotThrow(
        () -> AttestationDirectoryDurability.requireNativeAccess(getClass().getModule()));
  }

  @Test
  void describesBothNativeAbisWithoutExecutingTheOtherPlatform() {
    assertEquals(
        AttestationDirectoryPlatformSpec.POSIX,
        AttestationDirectoryPlatformSpec.forOperatingSystem(
            AttestationDirectoryDurability.OperatingSystem.POSIX));
    assertEquals(
        AttestationDirectoryPlatformSpec.WINDOWS,
        AttestationDirectoryPlatformSpec.forOperatingSystem(
            AttestationDirectoryDurability.OperatingSystem.WINDOWS));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment posixPath =
          AttestationDirectoryPlatformSpec.POSIX.nativePath(arena, temporaryDirectory);
      MemorySegment windowsPath =
          AttestationDirectoryPlatformSpec.WINDOWS.nativePath(arena, temporaryDirectory);
      assertEquals(2, AttestationDirectoryPlatformSpec.POSIX.openArguments(posixPath).length);
      assertEquals(7, AttestationDirectoryPlatformSpec.WINDOWS.openArguments(windowsPath).length);
      assertEquals(0, windowsPath.get(ValueLayout.JAVA_BYTE, windowsPath.byteSize() - 1));
      assertEquals(0, windowsPath.get(ValueLayout.JAVA_BYTE, windowsPath.byteSize() - 2));
      assertTrue(AttestationDirectoryPlatformSpec.POSIX.isInvalid(-1));
      assertFalse(AttestationDirectoryPlatformSpec.POSIX.isInvalid(0));
      assertTrue(AttestationDirectoryPlatformSpec.WINDOWS.isInvalid(MemorySegment.ofAddress(-1)));
      assertFalse(AttestationDirectoryPlatformSpec.WINDOWS.isInvalid(MemorySegment.NULL));
      assertEquals(0, AttestationDirectoryPlatformSpec.POSIX.normalizeCompletionStatus(0));
      assertEquals(-1, AttestationDirectoryPlatformSpec.POSIX.normalizeCompletionStatus(-1));
      assertEquals(1, AttestationDirectoryPlatformSpec.WINDOWS.normalizeCompletionStatus(0));
      assertEquals(0, AttestationDirectoryPlatformSpec.WINDOWS.normalizeCompletionStatus(1));
      assertEquals(1, AttestationDirectoryPlatformSpec.POSIX.handleArguments(0).length);
      assertEquals(
          1, AttestationDirectoryPlatformSpec.WINDOWS.handleArguments(MemorySegment.NULL).length);
    }
  }

  @Test
  void bindsTheCurrentPlatformAndValidatesTheForeignPlatformDeclaration() throws Exception {
    AttestationDirectoryFfmTransport.NativeCallBinder binder =
        AttestationDirectoryFfmTransport.nativeCallBinder();
    AttestationDirectoryDurability.OperatingSystem operatingSystem =
        AttestationDirectoryDurability.operatingSystem(System.getProperty("os.name", ""));
    AttestationDirectoryPlatformSpec currentSpecification =
        AttestationDirectoryPlatformSpec.forOperatingSystem(operatingSystem);
    SymbolLookup currentLookup =
        currentSpecification.usesDefaultLookup()
            ? Linker.nativeLinker().defaultLookup()
            : AttestationDirectoryFfmTransport.libraryLookup(currentSpecification.libraryName());
    AttestationDirectoryDurability.PlatformBinding current =
        binder.bind(currentSpecification, currentLookup);
    try (AttestationDirectoryDurability.DirectoryHandle handle = current.open(temporaryDirectory)) {
      assertFalse(current.isInvalid(handle));
      assertEquals(0, current.flush(handle));
      assertEquals(0, current.close(handle));
    }

    AttestationDirectoryPlatformSpec foreignSpecification =
        currentSpecification == AttestationDirectoryPlatformSpec.POSIX
            ? AttestationDirectoryPlatformSpec.WINDOWS
            : AttestationDirectoryPlatformSpec.POSIX;
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
            () -> binder.bind(AttestationDirectoryPlatformSpec.POSIX, ignored -> Optional.empty()));
    assertDurabilityFailure(missingSymbol);
    assertTrue(missingSymbol.getCause() instanceof IllegalStateException);
  }

  @Test
  void resolvesOnlyTheCurrentNativeLibraryAndKeepsForeignTransportTestable() throws Exception {
    SymbolLookup loadedLibrary =
        AttestationDirectoryFfmTransport.libraryLookup(currentPlatformLibraryName());
    assertTrue(loadedLibrary.find(currentPlatformProbeSymbol()).isPresent());
    IOException missingLibrary =
        assertThrows(
            IOException.class,
            () ->
                AttestationDirectoryFfmTransport.libraryLookup("fingrind-library-does-not-exist"));
    assertDurabilityFailure(missingLibrary);

    AtomicReference<AttestationDirectoryPlatformSpec> boundSpecification = new AtomicReference<>();
    AtomicReference<String> loadedLibraryName = new AtomicReference<>();
    RecordingBinding expectedBinding = new RecordingBinding(false, 0, null, 0, null);
    AttestationDirectoryFfmTransport.FfmDirectoryOperations operations =
        new AttestationDirectoryFfmTransport.FfmDirectoryOperations(
            (specification, ignored) -> {
              boundSpecification.set(specification);
              return expectedBinding;
            },
            libraryName -> {
              loadedLibraryName.set(libraryName);
              return Linker.nativeLinker().defaultLookup();
            });
    assertSame(
        expectedBinding, operations.binding(AttestationDirectoryDurability.OperatingSystem.POSIX));
    assertEquals(AttestationDirectoryPlatformSpec.POSIX, boundSpecification.get());
    assertSame(
        expectedBinding,
        operations.binding(AttestationDirectoryDurability.OperatingSystem.WINDOWS));
    assertEquals(AttestationDirectoryPlatformSpec.WINDOWS, boundSpecification.get());
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
    AttestationDirectoryFfmTransport.FfmPlatformBinding checkedBinding =
        new AttestationDirectoryFfmTransport.FfmPlatformBinding(
            AttestationDirectoryPlatformSpec.POSIX, checkedFailure, checkedFailure, checkedFailure);
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
    AttestationDirectoryFfmTransport.FfmPlatformBinding runtimeBinding =
        new AttestationDirectoryFfmTransport.FfmPlatformBinding(
            AttestationDirectoryPlatformSpec.POSIX, runtimeFailure, runtimeFailure, runtimeFailure);
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
    assertEquals(
        "Failed to make the published attestation-controlled artifact directory durable.",
        exception.getMessage());
  }

  /** Simulates a native platform binding and records both native close and resource release. */
  private static final class RecordingBinding
      implements AttestationDirectoryDurability.PlatformBinding {
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
    public AttestationDirectoryDurability.DirectoryHandle open(Path directory) {
      return handle;
    }

    @Override
    public boolean isInvalid(AttestationDirectoryDurability.DirectoryHandle ignored) {
      return invalid;
    }

    @Override
    public int flush(AttestationDirectoryDurability.DirectoryHandle ignored) throws IOException {
      if (flushFailure != null) {
        throw flushFailure;
      }
      return flushResult;
    }

    @Override
    public int close(AttestationDirectoryDurability.DirectoryHandle ignored) throws IOException {
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
      implements AttestationDirectoryDurability.DirectoryHandle {
    private static final RecordingHandle INSTANCE = new RecordingHandle();
    private boolean released;

    @Override
    public void close() {
      released = true;
    }
  }
}
