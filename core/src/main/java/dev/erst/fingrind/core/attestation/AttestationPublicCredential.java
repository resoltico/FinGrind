package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Public Ed25519 credential material used to identify one attestation signing key. */
public final class AttestationPublicCredential {
  private final byte[] spki;
  private final byte[] keyId;

  /** Validates and owns one canonical Ed25519 DER SubjectPublicKeyInfo value. */
  public AttestationPublicCredential(byte[] spki) {
    this.spki = Objects.requireNonNull(spki, "spki").clone();
    AttestationSpki checkedSpki = AttestationSpki.of(this.spki);
    if (!AttestationEd25519.isEd25519Spki(checkedSpki.bytes())) {
      throw new IllegalArgumentException("Attestation credential must be an Ed25519 DER SPKI.");
    }
    this.keyId = AttestationHash.sha256(checkedSpki.bytes()).bytes();
  }

  /** Returns a defensive copy of the canonical DER SubjectPublicKeyInfo encoding. */
  public byte[] spki() {
    return spki.clone();
  }

  /** Returns a defensive copy of SHA-256(SPKI). */
  public byte[] keyId() {
    return keyId.clone();
  }
}
