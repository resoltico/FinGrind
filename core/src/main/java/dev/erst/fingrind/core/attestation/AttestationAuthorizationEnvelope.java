package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Raw envelope entries retained in received order for deterministic authorization checks. */
final class AttestationAuthorizationEnvelope {
  private final byte[] payload;
  private final List<AttestationSignatureEntry> entries;

  AttestationAuthorizationEnvelope(byte[] payload, List<AttestationSignatureEntry> entries) {
    this.payload = AttestationEncoding.copy(payload, "payload");
    this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
  }

  byte[] payload() {
    return payload.clone();
  }

  List<AttestationSignatureEntry> entries() {
    return entries;
  }
}
