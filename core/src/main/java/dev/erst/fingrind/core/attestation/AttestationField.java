package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Presence-marked field in a canonical immutable-preimage record. */
final class AttestationField {
  private static final AttestationField ABSENT = new AttestationField(null);
  private final @Nullable AttestationFieldValue value;

  private AttestationField(@Nullable AttestationFieldValue value) {
    this.value = value;
  }

  static AttestationField absent() {
    return ABSENT;
  }

  static AttestationField present(AttestationFieldValue value) {
    return new AttestationField(Objects.requireNonNull(value, "value"));
  }

  boolean isPresent() {
    return value != null;
  }

  byte[] encoded() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    appendTo(output);
    return output.toByteArray();
  }

  void appendTo(ByteArrayOutputStream output) {
    Objects.requireNonNull(output, "output");
    AttestationUnsignedEncoding.appendByte(output, value == null ? 0 : 1, "field presence");
    if (value != null) {
      output.writeBytes(value.encoded());
    }
  }

  boolean matches(AttestationFieldSchema schema) {
    Objects.requireNonNull(schema, "schema");
    return value == null ? !schema.required() : value.type() == schema.type();
  }
}
