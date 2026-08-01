package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One valid-operation compromise-review finding, never a structural verification failure. */
public record AttestationReviewFinding(
    AttestationCompromiseReview compromiseReview, BigInteger operationOrder) {
  /** Binds one validated review declaration to one affected accepted operation. */
  public AttestationReviewFinding {
    Objects.requireNonNull(compromiseReview, "compromiseReview");
    operationOrder =
        AttestationUnsignedEncoding.requireUnsigned(operationOrder, Long.BYTES, "operationOrder");
  }

  /**
   * Validates and immutably copies findings asserted by a successful immutable-chain verification.
   *
   * <p>Each finding must identify an accepted operation no later than the verified head, fall
   * within its declaration's inclusive review interval, and occur once for that declaration and
   * operation pair.
   */
  public static List<AttestationReviewFinding> requireValidForVerifiedHead(
      BigInteger verifiedHeadOrder, List<AttestationReviewFinding> findings) {
    BigInteger checkedHeadOrder =
        AttestationUnsignedEncoding.requireUnsigned(
            verifiedHeadOrder, Long.BYTES, "verifiedHeadOrder");
    List<AttestationReviewFinding> checkedFindings =
        List.copyOf(Objects.requireNonNull(findings, "findings"));
    Set<AttestationReviewFinding> seenFindings = new HashSet<>();
    for (AttestationReviewFinding finding : checkedFindings) {
      if (finding.operationOrder().compareTo(checkedHeadOrder) > 0) {
        throw new IllegalArgumentException(
            "A review finding must not exceed the verified attestation head order.");
      }
      if (!finding.compromiseReview().includes(finding.operationOrder())) {
        throw new IllegalArgumentException(
            "A review finding must fall within its declared inclusive review interval.");
      }
      if (!seenFindings.add(finding)) {
        throw new IllegalArgumentException(
            "A review declaration must not report the same operation more than once.");
      }
    }
    return checkedFindings;
  }
}
