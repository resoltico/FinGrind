package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;

/** Canonical numeric field-value encoders. */
final class AttestationNumericFieldValue {
  private AttestationNumericFieldValue() {}

  static AttestationFieldValue unsigned8(int value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.UNSIGNED_8,
        output -> AttestationUnsignedEncoding.appendByte(output, value, "unsigned8"));
  }

  static AttestationFieldValue unsigned16(int value) {
    return unsigned(
        BigInteger.valueOf(value), AttestationFieldType.UNSIGNED_16, Short.BYTES, "unsigned16");
  }

  static AttestationFieldValue unsigned32(BigInteger value) {
    return unsigned(value, AttestationFieldType.UNSIGNED_32, Integer.BYTES, "unsigned32");
  }

  static AttestationFieldValue unsigned64(BigInteger value) {
    return unsigned(value, AttestationFieldType.UNSIGNED_64, Long.BYTES, "unsigned64");
  }

  static AttestationFieldValue signed64(BigInteger value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.SIGNED_64,
        output -> AttestationUnsignedEncoding.appendSigned(output, value, Long.BYTES, "signed64"));
  }

  static AttestationFieldValue signed128(BigInteger value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.SIGNED_128,
        output -> AttestationUnsignedEncoding.appendSigned(output, value, 16, "signed128"));
  }

  static AttestationFieldValue money(String currency, boolean negative, BigInteger minorUnits) {
    if (minorUnits.signum() == 0 && negative) {
      throw new IllegalArgumentException("money zero must use the plus sign.");
    }
    return AttestationFieldValue.encode(
        AttestationFieldType.MONEY,
        output -> {
          AttestationTextEncoding.appendCurrency(output, currency);
          AttestationUnsignedEncoding.appendByte(output, negative ? 1 : 0, "money sign");
          AttestationUnsignedEncoding.appendUnsigned(output, minorUnits, 16, "money minorUnits");
        });
  }

  static AttestationFieldValue scaled(int scale, boolean negative, BigInteger units) {
    if (scale < 0 || scale > 18) {
      throw new IllegalArgumentException("scaled scale must be between 0 and 18.");
    }
    if (units.signum() == 0 && negative) {
      throw new IllegalArgumentException("scaled zero must use the plus sign.");
    }
    return AttestationFieldValue.encode(
        AttestationFieldType.SCALED,
        output -> {
          AttestationUnsignedEncoding.appendByte(output, scale, "scaled scale");
          AttestationUnsignedEncoding.appendByte(output, negative ? 1 : 0, "scaled sign");
          AttestationUnsignedEncoding.appendUnsigned(output, units, 16, "scaled units");
        });
  }

  static AttestationFieldValue booleanValue(boolean value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.BOOLEAN,
        output -> AttestationUnsignedEncoding.appendByte(output, value ? 1 : 0, "boolean"));
  }

  static AttestationFieldValue mutation(int value) {
    if (value < 0 || value > 6) {
      throw new IllegalArgumentException("mutation must be between 0 and 6.");
    }
    return AttestationFieldValue.encode(
        AttestationFieldType.MUTATION,
        output -> AttestationUnsignedEncoding.appendByte(output, value, "mutation"));
  }

  private static AttestationFieldValue unsigned(
      BigInteger value, AttestationFieldType type, int byteCount, String fieldName) {
    return AttestationFieldValue.encode(
        type,
        output -> AttestationUnsignedEncoding.appendUnsigned(output, value, byteCount, fieldName));
  }
}
