package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Exercises platform-neutral Win32 ABI value construction and deterministic failure ownership. */
class WindowsPrivateOutputFileNativeTest {
  @Test
  void encodesEveryExtendedPathFormAndNativeDataLayout() {
    try (Arena arena = Arena.ofConfined()) {
      assertEquals(
          "\\\\?\\C:\\private\\artifact.fg",
          decodeWide(
              WindowsPrivateOutputFileNative.extendedWidePath(arena, "C:\\private\\artifact.fg")));
      assertEquals(
          "\\\\?\\UNC\\server\\share\\artifact.fg",
          decodeWide(
              WindowsPrivateOutputFileNative.extendedWidePath(
                  arena, "\\\\server\\share\\artifact.fg")));
      assertEquals(
          "\\\\?\\C:\\already-extended.fg",
          decodeWide(
              WindowsPrivateOutputFileNative.extendedWidePath(
                  arena, "\\\\?\\C:\\already-extended.fg")));
      Path relativePath = Path.of("private-artifact.fg");
      assertEquals(
          "\\\\?\\" + relativePath.toAbsolutePath().normalize(),
          decodeWide(WindowsPrivateOutputFileNative.extendedWidePath(arena, relativePath)));
      assertEquals("value", decodeWide(WindowsPrivateOutputFileNative.wideString(arena, "value")));
      assertEquals(16L, WindowsPrivateOutputFileNative.alignUp(13L, 8L));
      assertEquals(0x89ab_cdef, WindowsPrivateOutputFileNative.lowDword(0x0123_4567_89ab_cdefL));
      assertEquals(0x0123_4567, WindowsPrivateOutputFileNative.highDword(0x0123_4567_89ab_cdefL));

      MemorySegment overlapped =
          WindowsPrivateOutputFileNative.zeroedOverlapped(arena, 0x0123_4567_89ab_cdefL);
      long offset = 2L * ValueLayout.ADDRESS.byteSize();
      assertEquals(0x89ab_cdef, overlapped.get(ValueLayout.JAVA_INT, offset));
      assertEquals(0x0123_4567, overlapped.get(ValueLayout.JAVA_INT, offset + Integer.BYTES));
    }
  }

  @Test
  void decodesAnExactlySizedNullTerminatedSidTextAllocation() throws IOException {
    String expected = "S-1-5-21-42";
    byte[] encoded = (expected + "\0").getBytes(StandardCharsets.UTF_16LE);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text = arena.allocate(encoded.length, Character.BYTES);
      text.asByteBuffer().put(encoded);

      assertEquals(expected, WindowsPrivateOutputFileSid.decodeBoundedUtf16LeZ(text));
    }
  }

  @Test
  void rejectsSidTextWithoutATerminatorInsideTheBoundedNativeAllocation() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text =
          arena
              .allocate(WindowsPrivateOutputFileNative.MAXIMUM_SID_STRING_BYTES, Character.BYTES)
              .fill((byte) 0x41);

      IOException failure =
          assertThrows(
              IOException.class, () -> WindowsPrivateOutputFileSid.decodeBoundedUtf16LeZ(text));

      assertEquals("Windows SID text exceeded its bounded buffer.", failure.getMessage());
    }
  }

  @Test
  void mapsNativeFailuresAndPreservesPrimaryFailuresDuringCleanup() throws IOException {
    NativeCalls calls = new NativeCalls();
    WindowsPrivateOutputFileNative.Handle handle = new WindowsPrivateOutputFileNative.Handle(0x41L);

    assertDoesNotThrow(() -> WindowsPrivateOutputFileNative.closeHandle(calls.fileCalls(), handle));
    assertDoesNotThrow(
        () ->
            WindowsPrivateOutputFileNative.localFree(
                calls.ownerCalls(), MemorySegment.ofAddress(0x42L)));
    assertDoesNotThrow(
        () ->
            WindowsPrivateOutputFileNative.requireTrue(
                new WindowsPrivateOutputFileNative.Result<>(1, 0), "Operation"));

    calls.closeResult = new WindowsPrivateOutputFileNative.Result<>(0, 5);
    IOException closeFailure =
        assertThrows(
            IOException.class,
            () -> WindowsPrivateOutputFileNative.closeHandle(calls.fileCalls(), handle));
    assertTrue(String.valueOf(closeFailure.getMessage()).contains("5"));
    RuntimeException primary = new RuntimeException("primary");
    WindowsPrivateOutputFileNative.closePreservingFailure(calls.fileCalls(), handle, primary);
    assertEquals(1, primary.getSuppressed().length);

    calls.localFreeResult = new WindowsPrivateOutputFileNative.Result<>(1L, 6);
    IOException localFreeFailure =
        assertThrows(
            IOException.class,
            () ->
                WindowsPrivateOutputFileNative.localFree(
                    calls.ownerCalls(), MemorySegment.ofAddress(0x43L)));
    assertTrue(String.valueOf(localFreeFailure.getMessage()).contains("6"));
    IOException requiredFailure =
        assertThrows(
            IOException.class,
            () ->
                WindowsPrivateOutputFileNative.requireTrue(
                    new WindowsPrivateOutputFileNative.Result<>(0, 7), "Operation"));
    assertTrue(String.valueOf(requiredFailure.getMessage()).contains("7"));
    assertThrows(
        IllegalArgumentException.class, () -> new WindowsPrivateOutputFileNative.Handle(0L));
    assertThrows(
        IllegalArgumentException.class, () -> new WindowsPrivateOutputFileNative.Handle(-1L));
  }

  @Test
  void nativeBoundaryChecksAccessBeforeReturningTheFreshCallTable() throws IOException {
    NativeCalls nativeCalls = new NativeCalls();
    WindowsPrivateOutputFileCalls expected = nativeCalls.calls();

    assertSame(expected, WindowsPrivateOutputFileNative.calls(() -> expected));
    IOException accessFailure =
        assertThrows(
            IOException.class,
            () -> WindowsPrivateOutputFileNative.requireNativeAccess(false, "FinGrind"));
    assertTrue(
        String.valueOf(accessFailure.getMessage()).contains("--enable-native-access=FinGrind"));
    assertDoesNotThrow(() -> WindowsPrivateOutputFileNative.requireNativeAccess(true, "ignored"));
    assertThrows(
        NullPointerException.class,
        () -> WindowsPrivateOutputFileNative.requireNativeAccess(false, nullOf()));
  }

  private static String decodeWide(MemorySegment segment) {
    byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);
    int length = 0;
    while (length + 1 < bytes.length && (bytes[length] != 0 || bytes[length + 1] != 0)) {
      length += Character.BYTES;
    }
    return new String(bytes, 0, length, StandardCharsets.UTF_16LE);
  }

  /** Provides only the explicit native outcomes needed by shared ABI helper tests. */
  private static final class NativeCalls {
    private WindowsPrivateOutputFileNative.Result<Integer> closeResult =
        new WindowsPrivateOutputFileNative.Result<>(1, 0);
    private WindowsPrivateOutputFileNative.Result<Long> localFreeResult =
        new WindowsPrivateOutputFileNative.Result<>(0L, 0);
    private final WindowsPrivateOutputFileHandleCalls fileCalls =
        new WindowsPrivateOutputFileCallTestSupport.HandleCalls() {
          @Override
          public WindowsPrivateOutputFileNative.Result<Integer> closeHandle(MemorySegment handle) {
            return closeResult;
          }
        };
    private final WindowsPrivateOutputFileOwnerCalls ownerCalls =
        new WindowsPrivateOutputFileCallTestSupport.OwnerCalls() {
          @Override
          public WindowsPrivateOutputFileNative.Result<Long> localFree(MemorySegment allocation) {
            return localFreeResult;
          }
        };

    private WindowsPrivateOutputFileHandleCalls fileCalls() {
      return fileCalls;
    }

    private WindowsPrivateOutputFileOwnerCalls ownerCalls() {
      return ownerCalls;
    }

    private WindowsPrivateOutputFileCalls calls() {
      return new WindowsPrivateOutputFileCalls(
          fileCalls, ownerCalls, new WindowsPrivateOutputFileCallTestSupport.SecurityCalls());
    }
  }
}
