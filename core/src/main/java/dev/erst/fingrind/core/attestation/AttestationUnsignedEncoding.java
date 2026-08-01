package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Objects;

/** Fixed-width signed and unsigned integer encodings for the attestation format. */
final class AttestationUnsignedEncoding {
  private static final BigInteger UINT_8_LIMIT = BigInteger.ONE.shiftLeft(Byte.SIZE);
  private static final BigInteger UINT_16_LIMIT = BigInteger.ONE.shiftLeft(Short.SIZE);
  private static final BigInteger UINT_64_LIMIT = BigInteger.ONE.shiftLeft(Long.SIZE);

  private AttestationUnsignedEncoding() {}

  static void appendByte(ByteArrayOutputStream output, int value, String fieldName) {
    Objects.requireNonNull(output, "output");
    if (value < 0 || BigInteger.valueOf(value).compareTo(UINT_8_LIMIT) >= 0) {
      throw new IllegalArgumentException(fieldName + " must be an unsigned byte.");
    }
    output.write(value);
  }

  static void appendUnsigned(
      ByteArrayOutputStream output, BigInteger value, int byteCount, String fieldName) {
    Objects.requireNonNull(output, "output");
    requireUnsigned(value, byteCount, fieldName);
    byte[] valueBytes = value.toByteArray();
    int firstValueByte = valueBytes.length > 1 && valueBytes[0] == 0 ? 1 : 0;
    int encodedLength = valueBytes.length - firstValueByte;
    for (int index = encodedLength; index < byteCount; index++) {
      output.write(0);
    }
    output.write(valueBytes, firstValueByte, encodedLength);
  }

  static void appendSigned(
      ByteArrayOutputStream output, BigInteger value, int byteCount, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    int bitCount = Math.multiplyExact(byteCount, Byte.SIZE);
    BigInteger minimum = BigInteger.ONE.shiftLeft(bitCount - 1).negate();
    BigInteger maximum = BigInteger.ONE.shiftLeft(bitCount - 1).subtract(BigInteger.ONE);
    if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(
          fieldName + " must fit a signed " + byteCount + "-byte integer.");
    }
    byte[] valueBytes = value.toByteArray();
    byte pad = value.signum() < 0 ? (byte) 0xff : 0;
    for (int index = valueBytes.length; index < byteCount; index++) {
      output.write(pad);
    }
    output.writeBytes(valueBytes);
  }

  static BigInteger requireUnsigned(BigInteger value, int byteCount, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    BigInteger limit = BigInteger.ONE.shiftLeft(Math.multiplyExact(byteCount, Byte.SIZE));
    if (value.signum() < 0 || value.compareTo(limit) >= 0) {
      throw new IllegalArgumentException(
          fieldName + " must fit an unsigned " + byteCount + "-byte integer.");
    }
    return value;
  }

  static BigInteger uint16Limit() {
    return UINT_16_LIMIT;
  }

  static int uint16Maximum() {
    return UINT_16_LIMIT.subtract(BigInteger.ONE).intValueExact();
  }

  static BigInteger uint64Limit() {
    return UINT_64_LIMIT;
  }
}
