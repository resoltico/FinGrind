package dev.erst.fingrind.sqlite.secret;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.CharsetEncoder;
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
    String normalizedSource =
        SqliteBookPassphraseValidation.normalizeSourceDescription(sourceDescription);
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
    return fromCharactersDecision(
        sourceDescription, characters, SqliteBookPassphraseEncoding.utf8Encoder());
  }

  static ContractDecision<SqliteBookPassphrase> fromCharactersDecision(
      String sourceDescription, char[] characters, CharsetEncoder encoder) {
    String normalizedSource =
        SqliteBookPassphraseValidation.normalizeSourceDescription(sourceDescription);
    Objects.requireNonNull(characters, "characters");
    Objects.requireNonNull(encoder, "encoder");
    return SqliteBookPassphraseEncoding.fromCharactersDecision(
        normalizedSource, characters, encoder);
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

  /** Returns one defensive copy of the normalized UTF-8 passphrase bytes. */
  public byte[] utf8BytesCopy() {
    return utf8Bytes.clone();
  }

  /** Returns one independent owned passphrase instance with copied UTF-8 bytes. */
  public SqliteBookPassphrase copy() {
    return fromUtf8Bytes(sourceDescription, utf8BytesCopy());
  }

  @Override
  public void close() {
    Arrays.fill(utf8Bytes, (byte) 0);
  }

  static String normalizeSourceDescription(String sourceDescription) {
    return SqliteBookPassphraseValidation.normalizeSourceDescription(sourceDescription);
  }

  private static ContractDecision<byte[]> normalizeLoadedBytesDecision(
      byte[] loadedBytes, String sourceDescription) {
    return SqliteBookPassphraseEncoding.normalizeLoadedBytesDecision(
        loadedBytes, sourceDescription);
  }

  private static ContractDecision<SqliteBookPassphrase> rejectedAfterZeroizing(
      byte[] loadedBytes, dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    Arrays.fill(loadedBytes, (byte) 0);
    return ContractDecision.rejected(failure);
  }

  static void zeroize(java.nio.ByteBuffer encodedBytes) {
    SqliteBookPassphraseZeroization.zeroize(encodedBytes);
  }

  static void zeroize(java.nio.CharBuffer decodedCharacters) {
    SqliteBookPassphraseZeroization.zeroize(decodedCharacters);
  }
}
