package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * UTF-8 book passphrase bytes that can be copied into native memory exactly once and then
 * best-effort zeroized on the Java heap.
 */
public final class SqliteBookPassphrase implements AutoCloseable {
  public static final int MAX_UTF8_SOURCE_BYTES = 4096;

  private final String sourceDescription;
  private final byte[] utf8Bytes;

  private SqliteBookPassphrase(String sourceDescription, byte[] utf8Bytes) {
    this.sourceDescription = sourceDescription;
    this.utf8Bytes = utf8Bytes;
  }

  /**
   * Normalizes one raw UTF-8 passphrase payload.
   *
   * <p>The accepted instance takes ownership of the supplied bytes after normalization, so callers
   * must not retain or reuse the input array after calling this method.
   */
  public static SqliteBookPassphrase fromUtf8Bytes(String sourceDescription, byte[] loadedBytes) {
    return fromUtf8BytesDecision(sourceDescription, loadedBytes).requireAccepted();
  }

  /**
   * Normalizes one raw UTF-8 passphrase payload and returns the explicit accepted/rejected form.
   *
   * <p>The accepted instance takes ownership of the supplied bytes after normalization, so callers
   * must not retain or reuse the input array after calling this method. Rejected paths overwrite
   * the supplied bytes before returning.
   */
  public static ContractDecision<SqliteBookPassphrase> fromUtf8BytesDecision(
      String sourceDescription, byte[] loadedBytes) {
    return fromUtf8BytesDecision(
        sourceDescription, loadedBytes, SqliteBookPassphrase::normalizeLoadedBytesDecision);
  }

  static ContractDecision<SqliteBookPassphrase> fromUtf8BytesDecision(
      String sourceDescription,
      byte[] loadedBytes,
      BiFunction<byte[], String, ContractDecision<byte[]>> normalizer) {
    String normalizedSource = normalizeSourceDescription(sourceDescription);
    Objects.requireNonNull(loadedBytes, "loadedBytes");
    Objects.requireNonNull(normalizer, "normalizer");
    try {
      ContractDecision<byte[]> normalizedBytesDecision =
          normalizer.apply(loadedBytes, normalizedSource);
      switch (normalizedBytesDecision) {
        case ContractDecision.Accepted<byte[]>(byte[] normalizedBytes) -> {
          return ContractDecision.accepted(
              new SqliteBookPassphrase(normalizedSource, normalizedBytes));
        }
        case ContractDecision.Rejected<byte[]>(var failure) -> {
          return rejectedAfterZeroizing(loadedBytes, failure);
        }
      }
    } catch (RuntimeException | Error exception) {
      Arrays.fill(loadedBytes, (byte) 0);
      throw exception;
    }
  }

  /** Encodes one in-memory passphrase to UTF-8 and overwrites the supplied characters. */
  public static SqliteBookPassphrase fromCharacters(String sourceDescription, char[] characters) {
    return fromCharactersDecision(sourceDescription, characters).requireAccepted();
  }

  /** Encodes one in-memory passphrase to UTF-8 and returns the explicit accepted/rejected form. */
  public static ContractDecision<SqliteBookPassphrase> fromCharactersDecision(
      String sourceDescription, char[] characters) {
    return fromCharactersDecision(sourceDescription, characters, utf8Encoder());
  }

  static ContractDecision<SqliteBookPassphrase> fromCharactersDecision(
      String sourceDescription, char[] characters, CharsetEncoder encoder) {
    String normalizedSource = normalizeSourceDescription(sourceDescription);
    Objects.requireNonNull(characters, "characters");
    Objects.requireNonNull(encoder, "encoder");
    ByteBuffer encodedBytes = ByteBuffer.allocate(MAX_UTF8_SOURCE_BYTES + 1);
    try {
      CoderResult encodeResult = encoder.encode(CharBuffer.wrap(characters), encodedBytes, true);
      if (encodeResult.isOverflow()) {
        return ContractDecision.rejected(oversizedPassphraseSourceFailure(normalizedSource));
      }
      if (encodeResult.isError()) {
        return ContractDecision.rejected(invalidUtf8PassphraseSourceFailure(normalizedSource));
      }
      encoder.flush(encodedBytes);
      encodedBytes.flip();
      byte[] copiedBytes = new byte[encodedBytes.remaining()];
      encodedBytes.get(copiedBytes);
      return fromUtf8BytesDecision(normalizedSource, copiedBytes);
    } finally {
      zeroize(encodedBytes);
      Arrays.fill(characters, '\0');
    }
  }

  private static CharsetEncoder utf8Encoder() {
    return StandardCharsets.UTF_8
        .newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
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

  byte[] utf8BytesCopy() {
    return utf8Bytes.clone();
  }

  SqliteBookPassphrase copy() {
    return fromUtf8Bytes(sourceDescription, utf8BytesCopy());
  }

  @Override
  public void close() {
    Arrays.fill(utf8Bytes, (byte) 0);
  }

  static String normalizeSourceDescription(String sourceDescription) {
    Objects.requireNonNull(sourceDescription, "sourceDescription");
    String normalized = sourceDescription.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("sourceDescription must not be blank.");
    }
    return normalized;
  }

  private static byte[] ownedBytes(byte[] loadedBytes, int normalizedLength) {
    if (normalizedLength == loadedBytes.length) {
      return loadedBytes;
    }
    byte[] trimmedBytes = Arrays.copyOf(loadedBytes, normalizedLength);
    Arrays.fill(loadedBytes, (byte) 0);
    return trimmedBytes;
  }

  private static ContractDecision<byte[]> normalizeLoadedBytesDecision(
      byte[] loadedBytes, String sourceDescription) {
    if (loadedBytes.length > MAX_UTF8_SOURCE_BYTES) {
      return ContractDecision.rejected(oversizedPassphraseSourceFailure(sourceDescription));
    }
    ContractDecision<Integer> normalizedLengthDecision =
        normalizedPassphraseLength(loadedBytes, sourceDescription);
    switch (normalizedLengthDecision) {
      case ContractDecision.Accepted<Integer>(Integer normalizedLengthBoxed) -> {
        int normalizedLength = normalizedLengthBoxed.intValue();
        ContractDecision<String> validationDecision =
            validateTextPassphrase(loadedBytes, normalizedLength, sourceDescription);
        switch (validationDecision) {
          case ContractDecision.Accepted<String> _ -> {
            return ContractDecision.accepted(ownedBytes(loadedBytes, normalizedLength));
          }
          case ContractDecision.Rejected<String>(var failure) -> {
            return ContractDecision.rejected(failure);
          }
        }
      }
      case ContractDecision.Rejected<Integer>(var failure) -> {
        return ContractDecision.rejected(failure);
      }
    }
  }

  private static ContractDecision<SqliteBookPassphrase> rejectedAfterZeroizing(
      byte[] loadedBytes, dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    Arrays.fill(loadedBytes, (byte) 0);
    return ContractDecision.rejected(failure);
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure
      oversizedPassphraseSourceFailure(String sourceDescription) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "The FinGrind book passphrase source exceeded the %d-byte UTF-8 limit: %s"
            .formatted(MAX_UTF8_SOURCE_BYTES, sourceDescription),
        "Provide one non-empty single-line UTF-8 passphrase within the %d-byte limit through the selected passphrase source and rerun the command."
            .formatted(MAX_UTF8_SOURCE_BYTES),
        null);
  }

  private static ContractDecision<Integer> normalizedPassphraseLength(
      byte[] loadedBytes, String sourceDescription) {
    int endIndex = loadedBytes.length;
    if (endIndex > 0 && loadedBytes[endIndex - 1] == '\n') {
      endIndex--;
      if (endIndex > 0 && loadedBytes[endIndex - 1] == '\r') {
        endIndex--;
      }
    }
    if (endIndex == 0) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
              "The FinGrind book passphrase source must contain a non-empty UTF-8 passphrase: "
                  + sourceDescription,
              "Provide one non-empty UTF-8 passphrase through the selected key file, standard input, or interactive prompt route.",
              null));
    }
    return ContractDecision.accepted(endIndex);
  }

  private static ContractDecision<String> validateTextPassphrase(
      byte[] keyBytes, int keyLength, String sourceDescription) {
    CharBuffer decoded;
    try {
      decoded =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(keyBytes, 0, keyLength));
    } catch (CharacterCodingException exception) {
      return ContractDecision.rejected(invalidUtf8PassphraseSourceFailure(sourceDescription));
    }
    try {
      int offset = 0;
      while (offset < decoded.length()) {
        int codePoint = Character.codePointAt(decoded, offset);
        if (Character.isISOControl(codePoint)) {
          return ContractDecision.rejected(
              ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
                  "The FinGrind book passphrase source must contain a single-line UTF-8 text passphrase without control characters: "
                      + sourceDescription,
                  "Provide one single-line passphrase without control characters through the selected passphrase source and rerun the command.",
                  null));
        }
        offset += Character.charCount(codePoint);
      }
      return ContractDecision.accepted(sourceDescription);
    } finally {
      zeroize(decoded);
    }
  }

  static void zeroize(ByteBuffer encodedBytes) {
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

  static void zeroize(CharBuffer decodedCharacters) {
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

  private static dev.erst.fingrind.contract.runtime.ContractFailure
      invalidUtf8PassphraseSourceFailure(String sourceDescription) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "The FinGrind book passphrase source must contain a UTF-8 passphrase: " + sourceDescription,
        "Provide one UTF-8 passphrase payload through the selected passphrase source and rerun the command.",
        null);
  }
}
