package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/** Shared Win32 constants, ABI ownership helpers, and fresh narrowly-bound call tables. */
final class WindowsPrivateOutputFileNative {
  static final int ERROR_FILE_EXISTS = 80;
  static final int ERROR_ALREADY_EXISTS = 183;
  static final int ERROR_INSUFFICIENT_BUFFER = 122;
  static final int ERROR_NONE_MAPPED = 1_332;
  static final int ERROR_LOCK_VIOLATION = 33;
  static final int GENERIC_READ = 0x8000_0000;
  static final int GENERIC_WRITE = 0x4000_0000;
  static final int FILE_SHARE_READ_WRITE = 0x0000_0003;
  static final int CREATE_NEW = 1;
  static final int OPEN_EXISTING = 3;
  static final int FILE_ATTRIBUTE_NORMAL = 0x0000_0080;
  static final int FILE_ATTRIBUTE_DIRECTORY = 0x0000_0010;
  static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x0000_0400;
  static final int FILE_FLAG_OPEN_REPARSE_POINT = 0x0020_0000;
  static final int FILE_FLAG_BACKUP_SEMANTICS = 0x0200_0000;
  static final int FILE_BEGIN = 0;
  static final int LOCKFILE_FAIL_IMMEDIATELY = 0x0000_0001;
  static final int LOCKFILE_EXCLUSIVE_LOCK = 0x0000_0002;
  static final int TOKEN_QUERY = 0x0000_0008;
  static final int TOKEN_USER = 1;
  static final int SDDL_REVISION_1 = 1;
  static final int FILE_ID_INFO = 18;
  static final int FILE_ATTRIBUTE_TAG_INFO = 9;
  static final int SE_FILE_OBJECT = 1;
  static final int OWNER_SECURITY_INFORMATION = 0x0000_0001;
  static final int DACL_SECURITY_INFORMATION = 0x0000_0004;
  static final short SE_DACL_PROTECTED = 0x1000;
  static final int ACL_SIZE_INFORMATION = 2;
  static final int ACCESS_ALLOWED_ACE_TYPE = 0;
  static final int FILE_ALL_ACCESS = 0x001f_01ff;
  static final int MAXIMUM_SID_STRING_BYTES = 2_048;
  static final int MAXIMUM_SID_BINARY_BYTES = 68;
  static final int MAXIMUM_ACCOUNT_NAME_CHARACTERS = 32_768;
  static final int MAXIMUM_TRANSFER_BYTES = 64 * 1024;

  private WindowsPrivateOutputFileNative() {}

  /** Binds one fresh call table after enforcing the JVM native-access contract. */
  static WindowsPrivateOutputFileCalls calls(CallTableBinder callTableBinder) throws IOException {
    requireNativeAccess(
        WindowsPrivateOutputFileNative.class.getModule().isNativeAccessEnabled(),
        "dev.erst.fingrind.core");
    return Objects.requireNonNull(callTableBinder, "callTableBinder").bind();
  }

  static MemorySegment extendedWidePath(Arena arena, Path file) {
    String normalized =
        Objects.requireNonNull(file, "file").toAbsolutePath().normalize().toString();
    return extendedWidePath(arena, normalized);
  }

  /** Encodes one already-normalized Windows path in its long-path namespace form. */
  static MemorySegment extendedWidePath(Arena arena, String normalizedPath) {
    String normalized = Objects.requireNonNull(normalizedPath, "normalizedPath");
    String extended;
    if (normalized.startsWith("\\\\?\\")) {
      extended = normalized;
    } else if (normalized.startsWith("\\\\")) {
      extended = "\\\\?\\UNC\\" + normalized.substring(2);
    } else {
      extended = "\\\\?\\" + normalized;
    }
    return wideString(arena, extended);
  }

  static MemorySegment wideString(Arena arena, String value) {
    byte[] encoded = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_16LE);
    MemorySegment result = arena.allocate(encoded.length + Character.BYTES, Character.BYTES);
    result.asByteBuffer().put(encoded).put((byte) 0).put((byte) 0);
    return result;
  }

  static MemorySegment zeroedOverlapped(Arena arena, long position) {
    long pointerSize = ValueLayout.ADDRESS.byteSize();
    long eventOffset = alignUp(2L * pointerSize + 8L, pointerSize);
    MemorySegment overlapped =
        arena.allocate(eventOffset + pointerSize, pointerSize).fill((byte) 0);
    overlapped.set(ValueLayout.JAVA_INT, 2L * pointerSize, lowDword(position));
    overlapped.set(ValueLayout.JAVA_INT, 2L * pointerSize + Integer.BYTES, highDword(position));
    return overlapped;
  }

  static long alignUp(long value, long alignment) {
    return (value + alignment - 1L) & -alignment;
  }

  static int lowDword(long value) {
    return (int) value;
  }

  static int highDword(long value) {
    return (int) (value >>> Integer.SIZE);
  }

  static void closeHandle(WindowsPrivateOutputFileHandleCalls calls, Handle handle)
      throws IOException {
    requireTrue(
        Objects.requireNonNull(calls, "calls")
            .closeHandle(Objects.requireNonNull(handle, "handle").segment()),
        "CloseHandle");
  }

  static void closePreservingFailure(
      WindowsPrivateOutputFileHandleCalls calls, Handle handle, Throwable failure) {
    try {
      closeHandle(calls, handle);
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  static void localFree(WindowsPrivateOutputFileOwnerCalls calls, MemorySegment allocation)
      throws IOException {
    Result<Long> result =
        Objects.requireNonNull(calls, "calls")
            .localFree(Objects.requireNonNull(allocation, "allocation"));
    if (result.value() != 0L) {
      throw windowsFailure("LocalFree", result.lastError());
    }
  }

  static void requireTrue(Result<Integer> result, String operation) throws IOException {
    if (result.value() == 0) {
      throw windowsFailure(operation, result.lastError());
    }
  }

  static IOException windowsFailure(String operation, int error) {
    return new IOException(
        Objects.requireNonNull(operation, "operation")
            + " failed with Windows error "
            + Integer.toUnsignedString(error)
            + ".");
  }

  static void requireNativeAccess(boolean nativeAccessEnabled, String target) throws IOException {
    if (!nativeAccessEnabled) {
      throw new IOException(
          "Windows owner-only artifact creation requires JVM native access. Rerun with --enable-native-access="
              + Objects.requireNonNull(target, "target")
              + ".");
    }
  }

  /** Produces one full call table after the caller has entered the protected native boundary. */
  @FunctionalInterface
  interface CallTableBinder {
    /**
     * Binds a new complete native call table.
     *
     * @return the bound call table
     * @throws IOException if binding fails
     */
    WindowsPrivateOutputFileCalls bind() throws IOException;
  }

  record Handle(long bits) {
    Handle {
      if (bits == 0L || bits == -1L) {
        throw new IllegalArgumentException("Windows native handle must be valid.");
      }
    }

    MemorySegment segment() {
      return MemorySegment.ofAddress(bits);
    }
  }

  record Result<T>(T value, int lastError) {}
}
