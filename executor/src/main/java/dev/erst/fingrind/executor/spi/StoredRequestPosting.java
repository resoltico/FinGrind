package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;

/** One durably stored posting fact paired with its persisted semantic request fingerprint. */
public record StoredRequestPosting(
    CommittedPosting postingFact, RequestFingerprint requestFingerprint) {
  /** Validates one stored request-posting lookup result. */
  public StoredRequestPosting {
    Objects.requireNonNull(postingFact, "postingFact");
    Objects.requireNonNull(requestFingerprint, "requestFingerprint");
  }
}
