package dev.erst.fingrind.core.attestation;

import java.util.Arrays;
import java.util.List;

/** Reads typed field facts from a preimage that has already passed catalog validation. */
final class AttestationPreimageFields {
  private AttestationPreimageFields() {}

  static List<AttestationPreimage.Fact> records(AttestationPreimage preimage, int recordTypeTag) {
    return preimage.records().stream()
        .filter(fact -> fact.recordTypeTag() == recordTypeTag)
        .toList();
  }

  static void requireAbsent(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    if (requireField(fact, fieldIndex).isPresent()) {
      throw failure(failure);
    }
  }

  static void requireSameField(
      AttestationPreimage.Fact left,
      int leftIndex,
      AttestationPreimage.Fact right,
      int rightIndex,
      AttestationAuthorizationFailure failure) {
    if (!Arrays.equals(
        requireField(left, leftIndex).encoded(), requireField(right, rightIndex).encoded())) {
      throw failure(failure);
    }
  }

  static AttestationFieldValue requireValue(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return requireField(fact, fieldIndex).value().orElseThrow(() -> failure(failure));
  }

  static AttestationField requireField(AttestationPreimage.Fact fact, int fieldIndex) {
    return fact.fields().get(fieldIndex);
  }

  private static AttestationAuthorizationException failure(
      AttestationAuthorizationFailure failure) {
    return new AttestationAuthorizationException(failure);
  }
}
