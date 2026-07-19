package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/** Shared composition helpers for canonical attestation encodings. */
final class AttestationEncoding {
  static final String ALGORITHM_ID = "ed25519";

  private AttestationEncoding() {}

  static void appendUuid(ByteArrayOutputStream output, UUID value) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, "value");
    AttestationUnsignedEncoding.appendUnsigned(
        output,
        unsignedLong(value.getMostSignificantBits()),
        Long.BYTES,
        "uuid.mostSignificantBits");
    AttestationUnsignedEncoding.appendUnsigned(
        output,
        unsignedLong(value.getLeastSignificantBits()),
        Long.BYTES,
        "uuid.leastSignificantBits");
  }

  static void appendHash(ByteArrayOutputStream output, AttestationHash value) {
    Objects.requireNonNull(output, "output");
    output.writeBytes(Objects.requireNonNull(value, "value").bytes());
  }

  static byte[] copy(byte[] value, String fieldName) {
    return Objects.requireNonNull(value, fieldName).clone();
  }

  private static BigInteger unsignedLong(long value) {
    return BigInteger.valueOf(value)
        .and(AttestationUnsignedEncoding.uint64Limit().subtract(BigInteger.ONE));
  }
}
