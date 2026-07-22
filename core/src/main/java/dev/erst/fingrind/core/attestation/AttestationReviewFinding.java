package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;

/** One valid-operation compromise-review finding, never a structural verification failure. */
public record AttestationReviewFinding(
    AttestationCompromiseReview compromiseReview, BigInteger operationOrder) {
  /** Binds one validated review declaration to one affected accepted operation. */
  public AttestationReviewFinding {
    Objects.requireNonNull(compromiseReview, "compromiseReview");
    operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
  }
}
