package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Canonical low-level field encodings shared by attestation operation projections. */
final class AttestationPreimageProjectionFields {
  private AttestationPreimageProjectionFields() {}

  static AttestationField mutation() {
    return present(
        AttestationNumericFieldValue.mutation(AttestationEffectMutation.CREATE.wireValue()));
  }

  static AttestationField unsigned32(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("Unsigned 32-bit values must not be negative.");
    }
    return present(AttestationNumericFieldValue.unsigned32(BigInteger.valueOf(value)));
  }

  static AttestationField unsigned64(int value) {
    return present(AttestationNumericFieldValue.unsigned64(BigInteger.valueOf(value)));
  }

  static AttestationField uuid(UUID value) {
    return present(AttestationBinaryFieldValue.uuid(value));
  }

  static AttestationField optionalUuid(@Nullable String uuidText) {
    return Optional.ofNullable(uuidText)
        .<AttestationField>map(value -> uuid(UUID.fromString(value)))
        .orElseGet(AttestationField::absent);
  }

  static AttestationField optionalUuid(@Nullable UUID value) {
    return Optional.ofNullable(value)
        .<AttestationField>map(AttestationPreimageProjectionFields::uuid)
        .orElseGet(AttestationField::absent);
  }

  static AttestationField date(LocalDate value) {
    return present(AttestationTextFieldValue.date(value));
  }

  static AttestationField instant(Instant value) {
    return present(
        AttestationTextFieldValue.instant(
            java.util.Objects.requireNonNull(value, "value").truncatedTo(ChronoUnit.MILLIS)));
  }

  static AttestationField text(String value) {
    return present(AttestationTextFieldValue.text(value));
  }

  static AttestationField optionalText(@Nullable String value) {
    return value == null ? AttestationField.absent() : text(value);
  }

  static AttestationField token(String value) {
    return present(AttestationTextFieldValue.token(value));
  }

  static AttestationField money(String currencyCode, long magnitude, boolean negative) {
    return present(
        AttestationNumericFieldValue.money(currencyCode, negative, BigInteger.valueOf(magnitude)));
  }

  static AttestationField signedMoney(String currencyCode, long signedMinorUnits) {
    return present(
        AttestationNumericFieldValue.money(
            currencyCode, signedMinorUnits < 0, BigInteger.valueOf(signedMinorUnits).abs()));
  }

  static AttestationField present(AttestationFieldValue value) {
    return AttestationField.present(value);
  }
}
