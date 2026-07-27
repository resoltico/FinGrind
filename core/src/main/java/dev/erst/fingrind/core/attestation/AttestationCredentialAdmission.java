package dev.erst.fingrind.core.attestation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates semantic credential identity after custody has opened public credential material. */
public final class AttestationCredentialAdmission {
  private AttestationCredentialAdmission() {}

  /**
   * Requires each admitted public credential to represent a distinct public key, independent of
   * filesystem path aliases or hard links.
   */
  public static void requireDistinctPublicKeyIds(List<AttestationPublicCredential> credentials) {
    Set<AttestationHash> keyIds = new HashSet<>();
    for (AttestationPublicCredential credential :
        List.copyOf(Objects.requireNonNull(credentials, "credentials"))) {
      AttestationPublicCredential checkedCredential =
          Objects.requireNonNull(credential, "credentials must not contain null");
      byte[] keyId = checkedCredential.keyId();
      try {
        if (!keyIds.add(AttestationHash.of(keyId))) {
          throw AttestationAdmissionRejectedException.from(
              AttestationAuthorizationFailure.DUPLICATE_KEY);
        }
      } finally {
        java.util.Arrays.fill(keyId, (byte) 0);
      }
    }
  }
}
