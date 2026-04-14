package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * UTF-8 book passphrase bytes that can be copied into native memory exactly once and then zeroized.
 */
public final class SqliteBookPassphrase implements AutoCloseable {
  private final String sourceDescription;
  private final byte[] utf8Bytes;

  private SqliteBookPassphrase(String sourceDescription, byte[] utf8Bytes) {
    this.sourceDescription = sourceDescription;
    this.utf8Bytes = utf8Bytes;
  }

  /** Normalizes one raw UTF-8 passphrase payload and zeroizes the supplied source bytes. */
  public static SqliteBookPassphrase fromUtf8Bytes(String sourceDescription, byte[] loadedBytes) {
    String normalizedSource = normalizeSourceDescription(sourceDescription);
    Objects.requireNonNull(loadedBytes, "loadedBytes");
    try {
      byte[] normalizedBytes = stripTrailingLineEnding(loadedBytes, normalizedSource);
      validateTextPassphrase(normalizedBytes, normalizedSource);
      return new SqliteBookPassphrase(normalizedSource, normalizedBytes);
    } finally {
      Arrays.fill(loadedBytes, (byte) 0);
    }
  }

  /** Encodes one in-memory passphrase to UTF-8 and zeroizes the supplied characters. */
  public static SqliteBookPassphrase fromCharacters(String sourceDescription, char[] characters) {
    String normalizedSource = normalizeSourceDescription(sourceDescription);
    Objects.requireNonNull(characters, "characters");
    ByteBuffer encodedBytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(characters));
    try {
      byte[] copiedBytes = new byte[encodedBytes.remaining()];
      encodedBytes.get(copiedBytes);
      return fromUtf8Bytes(normalizedSource, copiedBytes);
    } finally {
      zeroize(encodedBytes);
      Arrays.fill(characters, '\0');
    }
  }

  /** Describes where the passphrase came from for diagnostics. */
  public String sourceDescription() {
    return sourceDescription;
  }

  /** Returns the number of passphrase bytes after normalization. */
  public int byteLength() {
    return utf8Bytes.length;
  }

  /** Copies the passphrase into one native null-terminated UTF-8 buffer. */
  public MemorySegment copyToCString(Arena arena) {
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

  private static String normalizeSourceDescription(String sourceDescription) {
    Objects.requireNonNull(sourceDescription, "sourceDescription");
    String normalized = sourceDescription.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("sourceDescription must not be blank.");
    }
    return normalized;
  }

  private static byte[] stripTrailingLineEnding(byte[] loadedBytes, String sourceDescription) {
    int endIndex = loadedBytes.length;
    if (endIndex > 0 && loadedBytes[endIndex - 1] == '\n') {
      endIndex--;
      if (endIndex > 0 && loadedBytes[endIndex - 1] == '\r') {
        endIndex--;
      }
    }
    if (endIndex == 0) {
      throw new IllegalStateException(
          "The FinGrind book passphrase source must contain a non-empty UTF-8 passphrase: "
              + sourceDescription);
    }
    return Arrays.copyOf(loadedBytes, endIndex);
  }

  private static void validateTextPassphrase(byte[] keyBytes, String sourceDescription) {
    CharBuffer decoded;
    try {
      decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(keyBytes));
    } catch (CharacterCodingException exception) {
      throw new IllegalStateException(
          "The FinGrind book passphrase source must contain a UTF-8 passphrase: "
              + sourceDescription,
          exception);
    }
    try {
      int offset = 0;
      while (offset < decoded.length()) {
        int codePoint = Character.codePointAt(decoded, offset);
        if (Character.isISOControl(codePoint)) {
          throw new IllegalStateException(
              "The FinGrind book passphrase source must contain a single-line UTF-8 text passphrase without control characters: "
                  + sourceDescription);
        }
        offset += Character.charCount(codePoint);
      }
    } finally {
      zeroize(decoded);
    }
  }

  private static void zeroize(ByteBuffer encodedBytes) {
    ByteBuffer duplicate = encodedBytes.duplicate();
    if (duplicate.hasArray()) {
      int startIndex = duplicate.arrayOffset();
      int endIndex = startIndex + duplicate.limit();
      Arrays.fill(duplicate.array(), startIndex, endIndex, (byte) 0);
      return;
    }
    for (int index = 0; index < duplicate.limit(); index++) {
      duplicate.put(index, (byte) 0);
    }
  }

  private static void zeroize(CharBuffer decodedCharacters) {
    CharBuffer duplicate = decodedCharacters.duplicate();
    if (duplicate.hasArray()) {
      int startIndex = duplicate.arrayOffset();
      int endIndex = startIndex + duplicate.limit();
      Arrays.fill(duplicate.array(), startIndex, endIndex, '\0');
      return;
    }
    for (int index = 0; index < duplicate.limit(); index++) {
      duplicate.put(index, '\0');
    }
  }
}
