package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Signals a declared compromise-review window that is not contained by an authenticated chain. */
public final class AttestationReviewWindowException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  private final String credentialKeyId;
  private final BigInteger firstAffectedOrder;
  private final @Nullable BigInteger lastAffectedOrder;
  private final BigInteger verifiedHeadOrder;

  /** Creates one typed refusal for a review window that extends beyond the verified head. */
  public AttestationReviewWindowException(
      AttestationCompromiseReview review, BigInteger verifiedHeadOrder) {
    super(
        "The declared compromise-review window is not contained by the verified attestation head.");
    AttestationCompromiseReview checkedReview = Objects.requireNonNull(review, "review");
    this.credentialKeyId = checkedReview.credentialKeyId();
    this.firstAffectedOrder = checkedReview.firstAffectedOrder();
    this.lastAffectedOrder = checkedReview.lastAffectedOrder();
    this.verifiedHeadOrder =
        AttestationUnsignedEncoding.requireUnsigned(
            verifiedHeadOrder, Long.BYTES, "verifiedHeadOrder");
  }

  /** Returns the non-persisted review declaration that exceeded the authenticated chain scope. */
  public AttestationCompromiseReview review() {
    return new AttestationCompromiseReview(credentialKeyId, firstAffectedOrder, lastAffectedOrder);
  }

  /** Returns the authenticated final operation order of the verified chain. */
  public BigInteger verifiedHeadOrder() {
    return verifiedHeadOrder;
  }
}
