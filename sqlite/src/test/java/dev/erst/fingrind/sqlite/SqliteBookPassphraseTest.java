package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Tests for {@link SqliteBookPassphrase}. */
class SqliteBookPassphraseTest {
  @Test
  void fromUtf8Bytes_normalizesPayloadAndZeroizesSourceBytes() {
    byte[] sourceBytes = "secret\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    try (SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromUtf8Bytes(" fixture ", sourceBytes)) {
      assertEquals("fixture", passphrase.sourceDescription());
      assertEquals(
          "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
          passphrase.byteLength());
    }

    assertArrayEquals(new byte[sourceBytes.length], sourceBytes);
  }

  @Test
  void copy_returnsIndependentOwnedPassphraseBytes() {
    byte[] sourceBytes = "secret\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    SqliteBookPassphrase original = SqliteBookPassphrase.fromUtf8Bytes("fixture", sourceBytes);
    SqliteBookPassphrase copied = original.copy();
    try (original;
        copied) {
      original.close();

      assertEquals("fixture", copied.sourceDescription());
      assertArrayEquals(
          "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), copied.utf8BytesCopy());
    }
  }

  @Test
  void normalizeSourceDescription_trimsAndRejectsBlankSourceDescriptions() {
    assertEquals(
        "secret source",
        SqliteBookPassphraseValidation.normalizeSourceDescription("  secret source  "));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookPassphraseValidation.normalizeSourceDescription("   "));

    assertEquals("sourceDescription must not be blank.", exception.getMessage());
  }

  @Test
  void fromCharactersDecision_rejectsMalformedUtf16InputAndZeroizesSourceCharacters() {
    char[] sourceCharacters = new char[] {'A', '\uD800', 'B'};

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromCharactersDecision(
                        " interactive prompt ", sourceCharacters)
                    .requireAccepted());
    String message = Objects.requireNonNull(exception.getMessage());

    assertTrue(
        message.contains(
            "The FinGrind book passphrase source must contain a UTF-8 passphrase: interactive prompt"));
    assertArrayEquals(new char[sourceCharacters.length], sourceCharacters);
  }

  @Test
  void fromCharactersDecision_rejectsEncoderReportedMalformedInputAndZeroizesSourceCharacters() {
    char[] sourceCharacters = new char[] {'s', 'e', 'c', 'r', 'e', 't'};

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromCharactersDecision(
                        "interactive prompt", sourceCharacters, new MalformedUtf8Encoder())
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage()).contains("must contain a UTF-8 passphrase"));
    assertArrayEquals(new char[sourceCharacters.length], sourceCharacters);
  }

  @Test
  void fromCharactersDecision_rejectsEncoderOverflowInputAndZeroizesSourceCharacters() {
    char[] sourceCharacters = "€".repeat(2049).toCharArray();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromCharactersDecision("interactive prompt", sourceCharacters)
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("exceeded the 4096-byte UTF-8 limit"));
    assertArrayEquals(new char[sourceCharacters.length], sourceCharacters);
  }

  @Test
  void fromCharactersDecision_rejectsBoundarySizedUtf8InputAndZeroizesSourceCharacters() {
    char[] sourceCharacters =
        "x".repeat(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1).toCharArray();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromCharactersDecision("interactive prompt", sourceCharacters)
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("exceeded the 4096-byte UTF-8 limit"));
    assertArrayEquals(new char[sourceCharacters.length], sourceCharacters);
  }

  @Test
  void fromUtf8BytesDecision_rejectsOversizedSourceBytesAndZeroizesSourceBytes() {
    byte[] sourceBytes = new byte[ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1];
    java.util.Arrays.fill(sourceBytes, (byte) 'x');

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromUtf8BytesDecision("fixture", sourceBytes)
                    .requireAccepted());

    assertTrue(
        Objects.requireNonNull(exception.getMessage())
            .contains("exceeded the 4096-byte UTF-8 limit"));
    assertArrayEquals(new byte[sourceBytes.length], sourceBytes);
  }

  @Test
  void fromUtf8BytesDecision_zeroizesSourceBytesWhenNormalizationThrows() {
    byte[] sourceBytes = "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookPassphrase.fromUtf8BytesDecision(
                    "fixture",
                    sourceBytes,
                    (ignoredBytes, ignoredSource) -> {
                      throw new IllegalStateException("boom");
                    }));

    assertEquals("boom", exception.getMessage());
    assertArrayEquals(new byte[sourceBytes.length], sourceBytes);
  }

  @Test
  void zeroize_overwritesArrayBackedBuffers() {
    ByteBuffer heapBytes = ByteBuffer.wrap(new byte[] {7, 8, 9, 10});

    SqliteBookPassphraseZeroization.zeroize(heapBytes);

    assertArrayEquals(new byte[4], heapBytes.array());
  }

  @Test
  void zeroize_overwritesDirectBuffers() {
    ByteBuffer directBytes = ByteBuffer.allocateDirect(4);
    directBytes.put(0, (byte) 7);
    directBytes.put(1, (byte) 8);
    directBytes.put(2, (byte) 9);
    directBytes.put(3, (byte) 10);

    SqliteBookPassphraseZeroization.zeroize(directBytes);

    byte[] actual = new byte[4];
    for (int index = 0; index < actual.length; index++) {
      actual[index] = directBytes.get(index);
    }
    assertArrayEquals(new byte[4], actual);
  }

  @Test
  void zeroize_overwritesDirectCharBuffers() {
    CharBuffer directCharacters = ByteBuffer.allocateDirect(8).asCharBuffer();
    directCharacters.put(0, 'a');
    directCharacters.put(1, 'b');
    directCharacters.put(2, 'c');
    directCharacters.put(3, 'd');

    SqliteBookPassphraseZeroization.zeroize(directCharacters);

    char[] actual = new char[4];
    for (int index = 0; index < actual.length; index++) {
      actual[index] = directCharacters.get(index);
    }
    assertArrayEquals(new char[4], actual);
  }

  /** Encoder fixture that reports one malformed result without writing any output bytes. */
  private static final class MalformedUtf8Encoder extends CharsetEncoder {
    private MalformedUtf8Encoder() {
      super(StandardCharsets.UTF_8, 1, 4);
      onMalformedInput(CodingErrorAction.REPORT);
      onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    @Override
    protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
      return CoderResult.malformedForLength(1);
    }

    @Override
    protected CoderResult implFlush(ByteBuffer out) {
      return CoderResult.UNDERFLOW;
    }
  }
}
