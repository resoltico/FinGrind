package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Public boundary for non-mutating, quorum-signed receipt artifacts. */
public final class AttestationReceipt {
  private static final int MAXIMUM_ENCODED_BYTE_COUNT =
      Math.addExact(
          Math.addExact(AttestationReceiptPayload.ENCODED_BYTE_COUNT, Short.BYTES),
          Math.multiplyExact(
              AttestationAuthorizationLimits.MAXIMUM_QUORUM,
              AttestationSignatureEntry.ENCODED_BYTE_COUNT));

  private AttestationReceipt() {}

  /** Returns the largest version-one receipt envelope accepted before receipt decoding begins. */
  public static int maximumEncodedByteCount() {
    return MAXIMUM_ENCODED_BYTE_COUNT;
  }

  /**
   * Verifies one receipt against the complete immutable evidence from its named book.
   *
   * <p>Receipt verification is intentionally non-mutating and reports retention quality separately
   * from cryptographic validity.
   */
  public static AttestationReceiptVerificationResult verify(
      byte[] receipt, List<AttestationEvidence> evidence, AttestationReceiptRetention retention) {
    requireMaximumEncodedByteCount(receipt);
    return AttestationArtifactVerifier.verifyReceiptArtifact(receipt, evidence, retention);
  }

  /** Rejects receipt bytes that cannot fit the current receipt-specific wire contract. */
  static void requireMaximumEncodedByteCount(byte[] receipt) {
    if (Objects.requireNonNull(receipt, "receipt").length > MAXIMUM_ENCODED_BYTE_COUNT) {
      throw new AttestationReceiptArtifactException(
          new AttestationAuthorizationException(AttestationAuthorizationFailure.RECEIPT_INVALID));
    }
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
    List<AttestationSigningCredential> checkedSigners =
        List.copyOf(Objects.requireNonNull(signers, "signers"));
    if (checkedSigners.size() > AttestationAuthorizationLimits.MAXIMUM_QUORUM) {
      throw new IllegalArgumentException(
          "Attestation receipt may contain at most "
              + AttestationAuthorizationLimits.MAXIMUM_QUORUM
              + " signatures.");
    }
    List<AttestationSignatureEntry> entries =
        checkedSigners.stream()
            .map(
                signer ->
                    Objects.requireNonNull(signer, "signers must not contain null")
                        .sign(payload.encoded()))
            .toList();
    return AttestationEnvelope.of(payload, entries).encoded();
  }
}
