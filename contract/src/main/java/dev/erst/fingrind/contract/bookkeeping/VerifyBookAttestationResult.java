package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
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
      String previousHeadHex,
      List<AttestationReviewFinding> reviewFindings,
      AttestationRegistryInspection registry)
      implements VerifyBookAttestationResult {
    public Valid {
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(headOrder, "headOrder");
      if (headOrder.signum() < 0 || headOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("headOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireHeadHex(operationHeadHex, "operationHeadHex");
      previousHeadHex = requireHeadHex(previousHeadHex, "previousHeadHex");
      if (headOrder.signum() == 0 && !previousHeadHex.equals("0".repeat(64))) {
        throw new IllegalArgumentException("previousHeadHex must be all-zero at genesis.");
      }
      reviewFindings =
          AttestationReviewFinding.requireValidForVerifiedHead(headOrder, reviewFindings);
      Objects.requireNonNull(registry, "registry");
      if (!bookId.equals(registry.bookId())
          || !headOrder.equals(registry.headOrder())
          || !operationHeadHex.equals(registry.operationHeadHex())) {
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
      failureCode =
          AttestationVerificationFailure.requireVerificationWireCode(
              failureCode, OperationId.VERIFY_BOOK);
    }
  }

  private static String requireHeadHex(String headHex, String name) {
    String value = Objects.requireNonNull(headHex, name);
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          name + " must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
