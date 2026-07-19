package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.function.Consumer;

/** Fully encoded non-null value carried by a present immutable-preimage field. */
final class AttestationFieldValue {
  private final AttestationFieldType type;
  private final byte[] encoded;

  private AttestationFieldValue(AttestationFieldType type, byte[] encoded) {
    this.type = type;
    this.encoded = encoded;
  }

  static AttestationFieldValue encode(
      AttestationFieldType type, Consumer<ByteArrayOutputStream> encoder) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(encoder, "encoder");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    encoder.accept(output);
    return new AttestationFieldValue(type, output.toByteArray());
  }

  AttestationFieldType type() {
    return type;
  }

  byte[] encoded() {
    return encoded.clone();
  }
}
