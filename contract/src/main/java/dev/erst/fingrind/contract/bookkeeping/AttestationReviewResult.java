package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.attestation.AttestationReviewFinding;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-persisted compromise-review report derived from one verified immutable chain. */
public record AttestationReviewResult(
    UUID bookId, BigInteger headOrder, List<AttestationReviewFinding> findings) {
  public AttestationReviewResult {
    Objects.requireNonNull(bookId, "bookId");
    Objects.requireNonNull(headOrder, "headOrder");
    if (headOrder.signum() < 0 || headOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("headOrder must be an unsigned 64-bit value.");
    }
    findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }
}
