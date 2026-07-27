package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Successful verification result for one complete immutable operation chain and its signed head
 * link.
 */
public final class AttestationVerification {
  private static final byte[] GENESIS_PREVIOUS_HEAD = new byte[32];

  private final UUID bookId;
  private final BigInteger headOrder;
  private final byte[] operationHead;
  private final byte[] previousHead;
  private final List<AttestationReviewFinding> reviewFindings;

  /**
   * Defensively owns the current and predecessor head bytes plus immutable compromise-review
   * findings.
   */
  public AttestationVerification(
      UUID bookId,
      BigInteger headOrder,
      byte[] operationHead,
      byte[] previousHead,
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
    this.previousHead = Objects.requireNonNull(previousHead, "previousHead").clone();
    if (this.previousHead.length != 32) {
      throw new IllegalArgumentException("previousHead must contain exactly 32 bytes.");
    }
    if (this.headOrder.signum() == 0 && !Arrays.equals(this.previousHead, GENESIS_PREVIOUS_HEAD)) {
      throw new IllegalArgumentException("previousHead must be all-zero at genesis.");
    }
    this.reviewFindings =
        AttestationReviewFinding.requireValidForVerifiedHead(this.headOrder, reviewFindings);
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

  /** Returns a defensive copy of the signed predecessor of the authenticated operation head. */
  public byte[] previousHead() {
    return previousHead.clone();
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
