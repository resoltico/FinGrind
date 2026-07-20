package dev.erst.fingrind.core.attestation;

import java.util.Arrays;

/** Immutable supplied SubjectPublicKeyInfo bytes retained by a credential binding. */
final class AttestationSpki {
  private final byte[] bytes;

  private AttestationSpki(byte[] bytes) {
    this.bytes = bytes;
  }

  static AttestationSpki of(byte[] bytes) {
    return new AttestationSpki(AttestationEncoding.copy(bytes, "spki"));
  }

  byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AttestationSpki spki && Arrays.equals(bytes, spki.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}
