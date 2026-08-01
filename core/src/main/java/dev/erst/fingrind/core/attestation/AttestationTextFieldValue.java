package dev.erst.fingrind.core.attestation;

import java.time.Instant;
import java.time.LocalDate;

/** Canonical text and temporal field-value encoders. */
final class AttestationTextFieldValue {
  private AttestationTextFieldValue() {}

  static AttestationFieldValue token(String value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.TOKEN,
        output -> AttestationTextEncoding.appendToken(output, value, "token"));
  }

  static AttestationFieldValue algorithmId(String value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.TOKEN,
        output -> AttestationTextEncoding.appendAlgorithmId(output, value));
  }

  static AttestationFieldValue text(String value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.TEXT,
        output -> AttestationTextEncoding.appendText(output, value, "text"));
  }

  static AttestationFieldValue currency(String value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.CURRENCY,
        output -> AttestationTextEncoding.appendCurrency(output, value));
  }

  static AttestationFieldValue date(LocalDate value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.DATE,
        output -> AttestationTextEncoding.appendDate(output, value, "date"));
  }

  static AttestationFieldValue instant(Instant value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.INSTANT,
        output -> AttestationTextEncoding.appendInstant(output, value, "instant"));
  }
}
