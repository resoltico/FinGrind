package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * Tests the fixed cryptographic primitive boundary used outside attestation-specific operations.
 */
class CryptographicPrimitivesTest {
  @Test
  void calculatesHmacSha256() {
    assertEquals(
        "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
        HexFormat.of()
            .formatHex(
                CryptographicPrimitives.hmacSha256(
                    "key".getBytes(StandardCharsets.UTF_8),
                    "The quick brown fox jumps over the lazy dog"
                        .getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void hmacSha256ReportsInvalidKeysAndUnavailableAlgorithms() {
    SecretKey rejectedKey =
        new SecretKey() {
          @Override
          public String getAlgorithm() {
            return "HmacSHA256";
          }

          @Override
          public String getFormat() {
            return "RAW";
          }

          @Override
          public byte[] getEncoded() {
            return nullOf();
          }
        };
    assertThrows(
        IllegalArgumentException.class,
        () -> CryptographicPrimitives.hmacSha256(rejectedKey, new byte[] {1}, "HmacSHA256"));
    assertThrows(
        IllegalStateException.class,
        () -> CryptographicPrimitives.hmacSha256(new byte[] {1}, new byte[] {1}, "not-an-hmac"));
  }

  @Test
  void runtimeHmacKeysExposeOnlyOneDefensiveRawEncoding() {
    RuntimeHmacKey key = new RuntimeHmacKey("HmacSHA256", new byte[] {1, 2, 3});
    byte[] firstEncoding = key.getEncoded();
    firstEncoding[0] = 9;

    assertEquals("HmacSHA256", key.getAlgorithm());
    assertEquals("RAW", key.getFormat());
    assertArrayEquals(new byte[] {1, 2, 3}, key.getEncoded());
  }

  private static final byte[] SHA_256_OF_ABC =
      HexFormat.of().parseHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

  @Test
  void sha256FormsProduceTheCanonicalDigest() throws Exception {
    byte[] value = "abc".getBytes(StandardCharsets.UTF_8);

    assertArrayEquals(SHA_256_OF_ABC, CryptographicPrimitives.sha256(value));
    assertEquals(
        HexFormat.of().formatHex(SHA_256_OF_ABC), CryptographicPrimitives.sha256Hex(value));
    assertEquals(
        HexFormat.of().formatHex(SHA_256_OF_ABC), CryptographicPrimitives.sha256HexUtf8("abc"));
    assertArrayEquals(
        SHA_256_OF_ABC, CryptographicPrimitives.sha256(new ByteArrayInputStream(value)));
    assertEquals(
        HexFormat.of().formatHex(SHA_256_OF_ABC),
        CryptographicPrimitives.sha256Hex(new ByteArrayInputStream(value)));
  }

  @Test
  void constantTimeEqualsDistinguishesMatchingAndDifferentValues() {
    assertTrue(CryptographicPrimitives.constantTimeEquals(new byte[] {1, 2}, new byte[] {1, 2}));
    assertFalse(CryptographicPrimitives.constantTimeEquals(new byte[] {1, 2}, new byte[] {1, 3}));
  }

  @Test
  void secureRandomProvidesAnEntropySource() {
    assertNotNull(CryptographicPrimitives.secureRandom());
    assertEquals(32, CryptographicPrimitives.secureBytes(32).length);
    assertEquals(0, CryptographicPrimitives.secureBytes(0).length);
    assertEquals(
        "byteCount must not be negative.",
        assertThrows(IllegalArgumentException.class, () -> CryptographicPrimitives.secureBytes(-1))
            .getMessage());
  }

  @Test
  void sha256ReportsUnavailableAlgorithm() {
    IllegalStateException bytesException =
        assertThrows(
            IllegalStateException.class,
            () ->
                CryptographicPrimitives.sha256(
                    new byte[0],
                    () -> {
                      throw new NoSuchAlgorithmException("missing");
                    }));
    IllegalStateException streamException =
        assertThrows(
            IllegalStateException.class,
            () ->
                CryptographicPrimitives.sha256Hex(
                    new ByteArrayInputStream(new byte[0]),
                    () -> {
                      throw new NoSuchAlgorithmException("missing");
                    }));

    assertEquals("SHA-256 is unavailable in this Java runtime.", bytesException.getMessage());
    assertEquals("SHA-256 is unavailable in this Java runtime.", streamException.getMessage());
  }

  @Test
  void sha256HexRejectsAnInputStreamThatCannotMakeReadProgress() {
    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                CryptographicPrimitives.sha256Hex(
                    new InputStream() {
                      @Override
                      public int read() {
                        return 0;
                      }

                      @Override
                      public int read(byte[] bytes) {
                        return 0;
                      }
                    }));

    assertEquals("Cryptographic digest input did not make read progress.", exception.getMessage());
  }
}
