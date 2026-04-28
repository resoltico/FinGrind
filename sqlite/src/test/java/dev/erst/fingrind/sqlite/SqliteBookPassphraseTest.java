package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
  void normalizeSourceDescription_trimsAndRejectsBlankSourceDescriptions() {
    assertEquals(
        "secret source", SqliteBookPassphrase.normalizeSourceDescription("  secret source  "));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteBookPassphrase.normalizeSourceDescription("   "));

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
  void zeroize_overwritesArrayBackedBuffers() {
    ByteBuffer heapBytes = ByteBuffer.wrap(new byte[] {7, 8, 9, 10});

    SqliteBookPassphrase.zeroize(heapBytes);

    assertArrayEquals(new byte[4], heapBytes.array());
  }

  @Test
  void zeroize_overwritesDirectBuffers() {
    ByteBuffer directBytes = ByteBuffer.allocateDirect(4);
    directBytes.put(0, (byte) 7);
    directBytes.put(1, (byte) 8);
    directBytes.put(2, (byte) 9);
    directBytes.put(3, (byte) 10);

    SqliteBookPassphrase.zeroize(directBytes);

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

    SqliteBookPassphrase.zeroize(directCharacters);

    char[] actual = new char[4];
    for (int index = 0; index < actual.length; index++) {
      actual[index] = directCharacters.get(index);
    }
    assertArrayEquals(new char[4], actual);
  }
}
