package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Successful verification result for one complete immutable operation chain. */
public final class AttestationVerification {
  private final UUID bookId;
  private final BigInteger headOrder;
  private final byte[] operationHead;
  private final List<AttestationReviewFinding> reviewFindings;

  /** Defensively owns the head bytes and immutable compromise-review findings. */
  public AttestationVerification(
      UUID bookId,
      BigInteger headOrder,
      byte[] operationHead,
      List<AttestationReviewFinding> reviewFindings) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.headOrder = Objects.requireNonNull(headOrder, "headOrder");
    if (this.headOrder.signum() < 0 || this.headOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("headOrder must be non-negative.");
    }
    this.operationHead = Objects.requireNonNull(operationHead, "operationHead").clone();
    if (this.operationHead.length != 32) {
      throw new IllegalArgumentException("operationHead must contain exactly 32 bytes.");
    }
    this.reviewFindings = List.copyOf(Objects.requireNonNull(reviewFindings, "reviewFindings"));
  }

  /** Returns the authenticated book identity. */
  public UUID bookId() {
    return bookId;
  }

  /** Returns the authenticated order of the current head. */
  public BigInteger headOrder() {
    return headOrder;
  }

  /** Returns a defensive copy of the authenticated operation head. */
  public byte[] operationHead() {
    return operationHead.clone();
  }

  /** Returns immutable, non-persisted compromise-review findings. */
  public List<AttestationReviewFinding> reviewFindings() {
    return reviewFindings;
  }

  /** Returns whether the chain is structurally valid but warrants compromise review. */
  public boolean reviewRequired() {
    return !reviewFindings.isEmpty();
  }
}
