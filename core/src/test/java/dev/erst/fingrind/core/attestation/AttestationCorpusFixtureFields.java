package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Encodes the canonical field values used by the static attestation corpus resources. */
final class AttestationCorpusFixtureFields {
  private AttestationCorpusFixtureFields() {}

  static AttestationField mutation() {
    return present(AttestationNumericFieldValue.mutation(0));
  }

  static AttestationField uuid(UUID value) {
    return present(AttestationBinaryFieldValue.uuid(value));
  }

  static AttestationField hash(AttestationHash value) {
    return present(AttestationBinaryFieldValue.hash(value));
  }

  static AttestationField spki(AttestationCorpusFixtureValues.Signer signer) {
    return present(AttestationBinaryFieldValue.spki(signer.keyPair().getPublic().getEncoded()));
  }

  static AttestationField token(String value) {
    return present(AttestationTextFieldValue.token(value));
  }

  static AttestationField text(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  static AttestationField currency() {
    return present(AttestationTextFieldValue.currency("EUR"));
  }

  static AttestationField date(LocalDate value) {
    return present(AttestationTextFieldValue.date(value));
  }

  static AttestationField instant(Instant value) {
    return present(AttestationTextFieldValue.instant(value));
  }

  static AttestationField u8(int value) {
    return present(AttestationNumericFieldValue.unsigned8(value));
  }

  static AttestationField u16(int value) {
    return present(AttestationNumericFieldValue.unsigned16(value));
  }

  static AttestationField u32(int value) {
    return present(AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(value)));
  }

  static AttestationField u64(long value) {
    return present(AttestationNumericFieldValue.unsigned64(BigInteger.valueOf(value)));
  }

  static AttestationField money(long minorUnits) {
    return present(
        AttestationNumericFieldValue.money("EUR", false, BigInteger.valueOf(minorUnits)));
  }

  static AttestationField bool(boolean value) {
    return present(AttestationNumericFieldValue.booleanValue(value));
  }

  static AttestationField optionalText(@Nullable String value) {
    return value == null ? absent() : text(value);
  }

  static AttestationField optionalHash(@Nullable AttestationHash value) {
    return value == null ? absent() : hash(value);
  }

  static AttestationField absent() {
    return AttestationField.absent();
  }

  static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }
}
