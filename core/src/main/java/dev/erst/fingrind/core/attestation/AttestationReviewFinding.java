package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;

/** One valid-operation compromise-review finding, never a structural verification failure. */
record AttestationReviewFinding(AttestationCompromiseReview review, BigInteger operationOrder) {
  AttestationReviewFinding {
    Objects.requireNonNull(review, "review");
    operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
  }
}
