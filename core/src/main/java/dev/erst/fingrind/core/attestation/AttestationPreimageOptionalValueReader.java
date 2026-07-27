package dev.erst.fingrind.core.attestation;

import org.jspecify.annotations.Nullable;

/** Decodes optional immutable-preimage fields while making their absence explicit to callers. */
final class AttestationPreimageOptionalValueReader {
  private AttestationPreimageOptionalValueReader() {}

  static @Nullable AttestationHash hash(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return AttestationPreimageFields.requireField(fact, fieldIndex).isPresent()
        ? AttestationPreimageValueReader.hash(fact, fieldIndex, failure)
        : null;
  }

  static @Nullable String text(
      AttestationPreimage.Fact fact, int fieldIndex, AttestationAuthorizationFailure failure) {
    return AttestationPreimageFields.requireField(fact, fieldIndex).isPresent()
        ? AttestationPreimageValueReader.text(fact, fieldIndex, failure)
        : null;
  }
}
