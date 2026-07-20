package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Successful receipt verification with its explicit trust-boundary finding. */
record AttestationReceiptVerification(
    AttestationDecodedEnvelope<AttestationReceiptPayload> receipt,
    List<AttestationReceiptFinding> findings) {
  AttestationReceiptVerification {
    Objects.requireNonNull(receipt, "receipt");
    findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }
}
