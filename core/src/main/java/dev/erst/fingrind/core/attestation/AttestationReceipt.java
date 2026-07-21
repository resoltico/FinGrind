package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public boundary for non-mutating, quorum-signed receipt artifacts. */
public final class AttestationReceipt {
  private AttestationReceipt() {}

  /**
   * Verifies one receipt against the complete immutable evidence from its named book.
   *
   * <p>Receipt verification is intentionally non-mutating and reports retention quality separately
   * from cryptographic validity.
   */
  public static AttestationReceiptVerificationResult verify(
      byte[] receipt, List<AttestationEvidence> evidence, AttestationReceiptRetention retention) {
    return AttestationArtifactVerifier.verifyReceiptArtifact(receipt, evidence, retention);
  }

  /** Creates one canonical quorum receipt. Only the signing-session boundary may invoke this. */
  static byte[] create(
      UUID bookId,
      BigInteger operationOrder,
      byte[] operationHead,
      Instant receiptTimestamp,
      List<AttestationSigningCredential> signers) {
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            Objects.requireNonNull(bookId, "bookId"),
            Objects.requireNonNull(operationOrder, "operationOrder"),
            AttestationHash.of(Objects.requireNonNull(operationHead, "operationHead")),
            Objects.requireNonNull(receiptTimestamp, "receiptTimestamp"));
    List<AttestationSignatureEntry> entries =
        List.copyOf(Objects.requireNonNull(signers, "signers")).stream()
            .map(
                signer ->
                    Objects.requireNonNull(signer, "signers must not contain null")
                        .sign(payload.encoded()))
            .toList();
    return AttestationEnvelope.of(payload, entries).encoded();
  }
}
