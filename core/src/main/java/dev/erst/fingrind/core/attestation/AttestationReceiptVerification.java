package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Successful receipt verification with its explicit trust-boundary finding. */
record AttestationReceiptVerification(
    AttestationDecodedEnvelope<AttestationReceiptPayload> receipt,
    boolean retainedWithinTrustBoundary) {
  AttestationReceiptVerification {
    Objects.requireNonNull(receipt, "receipt");
  }
}
