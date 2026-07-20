package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A non-persisted interval in which a credential must be reported for human review. */
record AttestationCompromiseReview(
    AttestationHash keyId, BigInteger firstAffectedOrder, @Nullable BigInteger lastAffectedOrder) {
  AttestationCompromiseReview {
    Objects.requireNonNull(keyId, "keyId");
    firstAffectedOrder =
        AttestationUnsignedEncoding.requireUnsigned(
            firstAffectedOrder, Long.BYTES, "firstAffectedOrder");
    if (lastAffectedOrder != null) {
      lastAffectedOrder =
          AttestationUnsignedEncoding.requireUnsigned(
              lastAffectedOrder, Long.BYTES, "lastAffectedOrder");
      if (lastAffectedOrder.compareTo(firstAffectedOrder) < 0) {
        throw new IllegalArgumentException(
            "lastAffectedOrder must not precede firstAffectedOrder.");
      }
    }
  }

  boolean includes(BigInteger order) {
    BigInteger checkedOrder = Objects.requireNonNull(order, "order");
    return checkedOrder.compareTo(firstAffectedOrder) >= 0
        && (lastAffectedOrder == null || checkedOrder.compareTo(lastAffectedOrder) <= 0);
  }
}
