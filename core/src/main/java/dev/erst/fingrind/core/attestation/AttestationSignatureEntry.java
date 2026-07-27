package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.UUID;

/** One principal/key/signature entry in a canonical attestation envelope. */
final class AttestationSignatureEntry {
  static final int SIGNATURE_BYTE_LENGTH = 64;
  static final int ENCODED_BYTE_COUNT = 16 + AttestationHash.BYTE_LENGTH + SIGNATURE_BYTE_LENGTH;
  private final UUID principalId;
  private final AttestationHash keyId;
  private final byte[] signature;

  AttestationSignatureEntry(UUID principalId, AttestationHash keyId, byte[] signature) {
    this.principalId = Objects.requireNonNull(principalId, "principalId");
    this.keyId = Objects.requireNonNull(keyId, "keyId");
    this.signature = AttestationEncoding.copy(signature, "signature");
    if (this.signature.length != SIGNATURE_BYTE_LENGTH) {
      throw new IllegalArgumentException("Attestation signature must contain exactly 64 bytes.");
    }
  }

  UUID principalId() {
    return principalId;
  }

  AttestationHash keyId() {
    return keyId;
  }

  byte[] signature() {
    return signature.clone();
  }

  void appendTo(ByteArrayOutputStream output) {
    AttestationEncoding.appendUuid(output, principalId);
    AttestationEncoding.appendHash(output, keyId);
    output.writeBytes(signature);
  }
}
