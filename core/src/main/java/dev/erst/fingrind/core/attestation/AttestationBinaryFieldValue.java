package dev.erst.fingrind.core.attestation;

import java.util.UUID;

/** Canonical binary identity and opaque-byte field-value encoders. */
final class AttestationBinaryFieldValue {
  private AttestationBinaryFieldValue() {}

  static AttestationFieldValue uuid(UUID value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.UUID, output -> AttestationEncoding.appendUuid(output, value));
  }

  static AttestationFieldValue hash(AttestationHash value) {
    return AttestationFieldValue.encode(
        AttestationFieldType.HASH, output -> AttestationEncoding.appendHash(output, value));
  }

  static AttestationFieldValue spki(byte[] value) {
    byte[] copiedValue = AttestationEncoding.copy(value, "value");
    return AttestationFieldValue.encode(
        AttestationFieldType.SPKI,
        output -> AttestationTextEncoding.appendSpki(output, copiedValue));
  }

  static AttestationFieldValue bytes(byte[] value) {
    byte[] copiedValue = AttestationEncoding.copy(value, "value");
    return AttestationFieldValue.encode(
        AttestationFieldType.BYTES,
        output -> AttestationTextEncoding.appendBytes(output, copiedValue, "bytes"));
  }

  static AttestationFieldValue embedded(byte[] value) {
    byte[] copiedValue = AttestationEncoding.copy(value, "value");
    return AttestationFieldValue.encode(
        AttestationFieldType.EMBEDDED,
        output -> AttestationTextEncoding.appendEmbedded(output, copiedValue, "embedded"));
  }
}
