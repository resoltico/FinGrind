package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Validates the distinct principal/key bindings that establish a protected book's founders. */
final class AttestationGenesisFounders {
  private AttestationGenesisFounders() {}

  static AttestationFounder founder(AttestationSigningCredential credential) {
    AttestationSigningCredential checkedCredential =
        Objects.requireNonNull(credential, "founders must not contain null");
    AttestationPublicCredential publicCredential = checkedCredential.publicCredential();
    return new AttestationFounder(
        checkedCredential.principalId(),
        AttestationHash.of(publicCredential.keyId()),
        AttestationSpki.of(publicCredential.spki()));
  }

  static void requireDistinctCredentials(List<AttestationFounder> founders) {
    long principalCount = founders.stream().map(AttestationFounder::principalId).distinct().count();
    long keyCount = founders.stream().map(AttestationFounder::keyId).distinct().count();
    if (principalCount != founders.size() || keyCount != founders.size()) {
      throw new IllegalArgumentException(
          "Genesis founders must have distinct principals and keys.");
    }
  }
}
