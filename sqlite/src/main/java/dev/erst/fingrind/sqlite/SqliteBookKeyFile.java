package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Reads one UTF-8 book passphrase file and zeroizes transient plaintext bytes after use. */
final class SqliteBookKeyFile {
  private SqliteBookKeyFile() {}

  static KeyMaterial load(Path bookKeyFilePath) {
    Path normalizedPath = normalize(bookKeyFilePath);
    byte[] loadedBytes = readBytes(normalizedPath);
    try {
      byte[] normalizedBytes = stripTrailingLineEnding(loadedBytes, normalizedPath);
      validateUtf8(normalizedBytes, normalizedPath);
      return new KeyMaterial(normalizedPath, normalizedBytes);
    } finally {
      Arrays.fill(loadedBytes, (byte) 0);
    }
  }

  private static Path normalize(Path bookKeyFilePath) {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    return bookKeyFilePath.toAbsolutePath().normalize();
  }

  private static byte[] readBytes(Path bookKeyFilePath) {
    try {
      return Files.readAllBytes(bookKeyFilePath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the FinGrind book key file: " + bookKeyFilePath, exception);
    }
  }

  private static byte[] stripTrailingLineEnding(byte[] loadedBytes, Path bookKeyFilePath) {
    int endIndex = loadedBytes.length;
    if (endIndex > 0 && loadedBytes[endIndex - 1] == '\n') {
      endIndex--;
      if (endIndex > 0 && loadedBytes[endIndex - 1] == '\r') {
        endIndex--;
      }
    }
    if (endIndex == 0) {
      throw new IllegalStateException(
          "The FinGrind book key file must contain a non-empty UTF-8 passphrase: "
              + bookKeyFilePath);
    }
    return Arrays.copyOf(loadedBytes, endIndex);
  }

  private static void validateUtf8(byte[] keyBytes, Path bookKeyFilePath) {
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(keyBytes));
    } catch (CharacterCodingException exception) {
      throw new IllegalStateException(
          "The FinGrind book key file must contain a UTF-8 passphrase: " + bookKeyFilePath,
          exception);
    }
  }

  /** Passphrase bytes that can be copied into native memory exactly once and then zeroized. */
  static final class KeyMaterial implements AutoCloseable {
    private final Path sourcePath;
    private final byte[] utf8Bytes;

    private KeyMaterial(Path sourcePath, byte[] utf8Bytes) {
      this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
      this.utf8Bytes = Objects.requireNonNull(utf8Bytes, "utf8Bytes");
    }

    Path sourcePath() {
      return sourcePath;
    }

    int byteLength() {
      return utf8Bytes.length;
    }

    MemorySegment copyToCString(Arena arena) {
      Objects.requireNonNull(arena, "arena");
      MemorySegment nativeBuffer = arena.allocate(utf8Bytes.length + 1L, 1L);
      nativeBuffer.asSlice(0, utf8Bytes.length).copyFrom(MemorySegment.ofArray(utf8Bytes));
      nativeBuffer.set(ValueLayout.JAVA_BYTE, utf8Bytes.length, (byte) 0);
      return nativeBuffer;
    }

    @Override
    public void close() {
      Arrays.fill(utf8Bytes, (byte) 0);
    }
  }
}
