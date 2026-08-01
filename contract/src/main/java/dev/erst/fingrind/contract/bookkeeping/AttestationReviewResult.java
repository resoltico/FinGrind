package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating result family for compromise review over one immutable attestation chain. */
public sealed interface AttestationReviewResult
    permits AttestationReviewResult.Valid, AttestationReviewResult.Invalid {

  /** Non-persisted compromise-review report bound to one verified immutable chain head. */
  record Valid(
      UUID bookId,
      BigInteger headOrder,
      String operationHeadHex,
      List<AttestationReviewFinding> findings)
      implements AttestationReviewResult {
    public Valid {
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(headOrder, "headOrder");
      if (headOrder.signum() < 0 || headOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("headOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireHeadHex(operationHeadHex);
      findings = AttestationReviewFinding.requireValidForVerifiedHead(headOrder, findings);
    }
  }

  /** First exact structural failure that prevented compromise review. */
  record Invalid(String failureCode) implements AttestationReviewResult {
    public Invalid {
      failureCode =
          AttestationVerificationFailure.requireVerificationWireCode(
              failureCode, OperationId.ATTESTATION_REVIEW);
    }
  }

  private static String requireHeadHex(String operationHeadHex) {
    String value = Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
