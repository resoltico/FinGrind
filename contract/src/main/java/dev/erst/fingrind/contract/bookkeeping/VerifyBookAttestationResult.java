package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating result family for a complete immutable book-attestation verification. */
public sealed interface VerifyBookAttestationResult
    permits VerifyBookAttestationResult.Valid, VerifyBookAttestationResult.Invalid {

  /** Complete valid chain and any non-persisted compromise-review findings. */
  record Valid(
      UUID bookId,
      BigInteger headOrder,
      String operationHeadHex,
      List<AttestationReviewFinding> reviewFindings,
      AttestationRegistryInspection registry)
      implements VerifyBookAttestationResult {
    public Valid {
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(headOrder, "headOrder");
      if (headOrder.signum() < 0 || headOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("headOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireOperationHeadHex(operationHeadHex);
      reviewFindings = List.copyOf(Objects.requireNonNull(reviewFindings, "reviewFindings"));
      Objects.requireNonNull(registry, "registry");
      if (!bookId.equals(registry.bookId()) || !headOrder.equals(registry.headOrder())) {
        throw new IllegalArgumentException("registry must describe the verified attestation head.");
      }
    }

    /** Returns whether a structurally valid chain nevertheless requires operator review. */
    public boolean reviewRequired() {
      return !reviewFindings.isEmpty();
    }
  }

  /** First exact structural attestation failure. */
  record Invalid(String failureCode) implements VerifyBookAttestationResult {
    public Invalid {
      failureCode = AttestationVerificationFailure.requireWireCode(failureCode);
    }
  }

  private static String requireOperationHeadHex(String operationHeadHex) {
    String value = Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
