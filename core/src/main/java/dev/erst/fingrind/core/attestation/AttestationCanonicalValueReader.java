package dev.erst.fingrind.core.attestation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

/** Reads canonical scalar values while retaining the raw reader as a bounded byte primitive. */
final class AttestationCanonicalValueReader {
  private AttestationCanonicalValueReader() {}

  static String token(AttestationByteReader input) {
    int start = input.offset();
    String value =
        new String(
            input.readBytes(input.readUnsigned(Byte.BYTES).intValueExact()),
            StandardCharsets.US_ASCII);
    requireCanonical(input, AttestationTextFieldValue.token(value).encoded(), start);
    return value;
  }

  static String text(AttestationByteReader input) {
    int start = input.offset();
    String value =
        new String(
            input.readBytes(input.readUnsigned(Integer.BYTES).intValueExact()),
            StandardCharsets.UTF_8);
    requireCanonical(input, AttestationTextFieldValue.text(value).encoded(), start);
    return value;
  }

  static String currency(AttestationByteReader input) {
    int start = input.offset();
    String value = new String(input.readBytes(3), StandardCharsets.US_ASCII);
    requireCanonical(input, AttestationTextFieldValue.currency(value).encoded(), start);
    return value;
  }

  static LocalDate date(AttestationByteReader input) {
    int start = input.offset();
    LocalDate value = LocalDate.parse(new String(input.readBytes(10), StandardCharsets.US_ASCII));
    requireCanonical(input, AttestationTextFieldValue.date(value).encoded(), start);
    return value;
  }

  static Instant instant(AttestationByteReader input) {
    int start = input.offset();
    Instant value = Instant.parse(new String(input.readBytes(24), StandardCharsets.US_ASCII));
    requireCanonical(input, AttestationTextFieldValue.instant(value).encoded(), start);
    return value;
  }

  static AttestationSpki spki(AttestationByteReader input) {
    int start = input.offset();
    byte[] value = input.readBytes(input.readUnsigned(Short.BYTES).intValueExact());
    requireCanonical(input, AttestationBinaryFieldValue.spki(value).encoded(), start);
    return AttestationSpki.of(value);
  }

  private static void requireCanonical(AttestationByteReader input, byte[] canonical, int start) {
    if (!Arrays.equals(canonical, input.sourceSlice(start, input.offset()))) {
      throw input.failure();
    }
  }
}
