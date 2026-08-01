package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A non-persisted interval in which a credential must be reported for human review. */
public record AttestationCompromiseReview(
    String credentialKeyId, BigInteger firstAffectedOrder, @Nullable BigInteger lastAffectedOrder) {
  /** Validates one canonical credential-key interval with inclusive endpoints. */
  public AttestationCompromiseReview {
    Objects.requireNonNull(credentialKeyId, "credentialKeyId");
    if (!credentialKeyId.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "credentialKeyId must contain 64 lowercase hexadecimal characters.");
    }
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

  /** Returns declarations in canonical order after rejecting duplicate or overlapping intervals. */
  public static List<AttestationCompromiseReview> canonicalize(
      List<AttestationCompromiseReview> reviews) {
    List<AttestationCompromiseReview> canonical =
        new ArrayList<>(List.copyOf(Objects.requireNonNull(reviews, "reviews")));
    canonical.replaceAll(review -> Objects.requireNonNull(review, "reviews must not contain null"));
    canonical.sort(
        Comparator.comparing(AttestationCompromiseReview::credentialKeyId)
            .thenComparing(AttestationCompromiseReview::firstAffectedOrder)
            .thenComparing(
                AttestationCompromiseReview::lastAffectedOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));
    AttestationCompromiseReview previous = null;
    for (AttestationCompromiseReview review : canonical) {
      if (previous != null
          && previous.credentialKeyId.equals(review.credentialKeyId)
          && (previous.lastAffectedOrder == null
              || review.firstAffectedOrder.compareTo(previous.lastAffectedOrder) <= 0)) {
        throw new IllegalArgumentException(
            "Compromise-review intervals for one credential must not overlap.");
      }
      previous = review;
    }
    return List.copyOf(canonical);
  }

  /** Returns canonical declarations only when every review interval fits the verified chain. */
  public static List<AttestationCompromiseReview> requireValidForVerifiedHead(
      BigInteger verifiedHeadOrder, List<AttestationCompromiseReview> reviews) {
    BigInteger checkedHeadOrder =
        AttestationUnsignedEncoding.requireUnsigned(
            verifiedHeadOrder, Long.BYTES, "verifiedHeadOrder");
    List<AttestationCompromiseReview> checkedReviews = canonicalize(reviews);
    for (AttestationCompromiseReview review : checkedReviews) {
      if (review.firstAffectedOrder().compareTo(checkedHeadOrder) > 0
          || (review.lastAffectedOrder() != null
              && review.lastAffectedOrder().compareTo(checkedHeadOrder) > 0)) {
        throw new AttestationReviewWindowException(review, checkedHeadOrder);
      }
    }
    return checkedReviews;
  }

  boolean includes(BigInteger order) {
    BigInteger checkedOrder = Objects.requireNonNull(order, "order");
    return checkedOrder.compareTo(firstAffectedOrder) >= 0
        && (lastAffectedOrder == null || checkedOrder.compareTo(lastAffectedOrder) <= 0);
  }

  AttestationHash keyId() {
    return AttestationHash.of(HexFormat.of().parseHex(credentialKeyId));
  }
}
