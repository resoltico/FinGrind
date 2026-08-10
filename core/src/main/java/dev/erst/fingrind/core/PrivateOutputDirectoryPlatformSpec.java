package dev.erst.fingrind.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/** Declares the native directory-durability ABI and its shared completion-status contract. */
enum PrivateOutputDirectoryPlatformSpec {
  POSIX(true, "not-applicable", "open", "fsync", "close"),
  WINDOWS(false, "kernel32", "CreateFileW", "FlushFileBuffers", "CloseHandle");

  private static final int OPEN_READ_ONLY = 0;
  private static final int GENERIC_WRITE = 0x40000000;
  private static final int FILE_SHARE_READ_WRITE_DELETE = 0x00000007;
  private static final int OPEN_EXISTING = 3;
  private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

  private final boolean usesDefaultLookup;
  private final String libraryName;
  private final String openSymbol;
  private final String flushSymbol;
  private final String closeSymbol;

  PrivateOutputDirectoryPlatformSpec(
      boolean usesDefaultLookup,
      String libraryName,
      String openSymbol,
      String flushSymbol,
      String closeSymbol) {
    this.usesDefaultLookup = usesDefaultLookup;
    this.libraryName = libraryName;
    this.openSymbol = openSymbol;
    this.flushSymbol = flushSymbol;
    this.closeSymbol = closeSymbol;
  }

  static PrivateOutputDirectoryPlatformSpec forOperatingSystem(
      PrivateOutputDirectoryDurability.OperatingSystem operatingSystem) {
    return switch (operatingSystem) {
      case POSIX -> POSIX;
      case WINDOWS -> WINDOWS;
    };
  }

  boolean usesDefaultLookup() {
    return usesDefaultLookup;
  }

  String libraryName() {
    return libraryName;
  }

  String openSymbol() {
    return openSymbol;
  }

  FunctionDescriptor openDescriptor() {
    return switch (this) {
      case POSIX ->
          FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
      case WINDOWS ->
          FunctionDescriptor.of(
              ValueLayout.ADDRESS,
              ValueLayout.ADDRESS,
              ValueLayout.JAVA_INT,
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS,
              ValueLayout.JAVA_INT,
              ValueLayout.JAVA_INT,
              ValueLayout.ADDRESS);
    };
  }

  String flushSymbol() {
    return flushSymbol;
  }

  FunctionDescriptor flushDescriptor() {
    return switch (this) {
      case POSIX -> FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
      case WINDOWS -> FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    };
  }

  String closeSymbol() {
    return closeSymbol;
  }

  FunctionDescriptor closeDescriptor() {
    return switch (this) {
      case POSIX -> FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
      case WINDOWS -> FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    };
  }

  MemorySegment nativePath(Arena arena, Path directory) {
    return switch (this) {
      case POSIX -> arena.allocateFrom(directory.toString());
      case WINDOWS ->
          widePath(
              arena, extendedLengthWindowsPath(directory.toAbsolutePath().normalize().toString()));
    };
  }

  Object[] openArguments(MemorySegment path) {
    return switch (this) {
      case POSIX -> new Object[] {path, OPEN_READ_ONLY};
      case WINDOWS ->
          new Object[] {
            path,
            GENERIC_WRITE,
            FILE_SHARE_READ_WRITE_DELETE,
            MemorySegment.NULL,
            OPEN_EXISTING,
            FILE_FLAG_BACKUP_SEMANTICS,
            MemorySegment.NULL
          };
    };
  }

  boolean isInvalid(Object nativeHandle) {
    return switch (this) {
      case POSIX -> (int) nativeHandle < 0;
      case WINDOWS -> ((MemorySegment) nativeHandle).address() == -1L;
    };
  }

  Object[] handleArguments(Object nativeHandle) {
    return new Object[] {nativeHandle};
  }

  int normalizeCompletionStatus(int nativeStatus) {
    return switch (this) {
      case POSIX -> nativeStatus;
      case WINDOWS -> nativeStatus == 0 ? 1 : 0;
    };
  }

  static String extendedLengthWindowsPath(String absolutePath) {
    Objects.requireNonNull(absolutePath, "absolutePath");
    if (absolutePath.startsWith("\\\\?\\")) {
      return absolutePath;
    }
    if (absolutePath.startsWith("\\\\")) {
      return "\\\\?\\UNC\\" + absolutePath.substring(2);
    }
    return "\\\\?\\" + absolutePath;
  }

  private static MemorySegment widePath(Arena arena, String directory) {
    byte[] encoded = directory.getBytes(StandardCharsets.UTF_16LE);
    MemorySegment path = arena.allocate(encoded.length + Character.BYTES, Character.BYTES);
    path.asByteBuffer().put(encoded).putChar('\0');
    return path;
  }
}
