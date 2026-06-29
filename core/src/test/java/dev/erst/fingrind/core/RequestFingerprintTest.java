package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Covers validation and stable defaults for {@link RequestFingerprint}. */
class RequestFingerprintTest {
  private static final String VALID_DIGEST =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  void acceptsCurrentVersionAndCanonicalDigest() {
    RequestFingerprint fingerprint =
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, VALID_DIGEST);

    assertEquals(RequestFingerprint.CURRENT_VERSION, fingerprint.version());
    assertEquals(VALID_DIGEST, fingerprint.sha256Hex());
  }

  @Test
  void rejectsNonPositiveVersion() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new RequestFingerprint(0, VALID_DIGEST));

    assertEquals("Request fingerprint version must be positive.", exception.getMessage());
  }

  @Test
  void rejectsNullOrNonCanonicalDigests() {
    NullPointerException nullDigest =
        assertThrows(
            NullPointerException.class,
            () -> new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, nullOf()));
    IllegalArgumentException uppercaseDigest =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestFingerprint(
                    RequestFingerprint.CURRENT_VERSION, VALID_DIGEST.toUpperCase(Locale.ROOT)));
    IllegalArgumentException shortDigest =
        assertThrows(
            IllegalArgumentException.class,
            () -> new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "abc123"));

    assertEquals("sha256Hex", nullDigest.getMessage());
    assertEquals(
        "Request fingerprint sha256Hex must be one lowercase 64-character hex digest.",
        uppercaseDigest.getMessage());
    assertEquals(
        "Request fingerprint sha256Hex must be one lowercase 64-character hex digest.",
        shortDigest.getMessage());
  }
}
