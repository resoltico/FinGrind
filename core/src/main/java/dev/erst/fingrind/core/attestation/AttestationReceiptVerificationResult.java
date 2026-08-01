package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Successful verification result for one independently retained attestation receipt. */
public final class AttestationReceiptVerificationResult {
  private final UUID bookId;
  private final BigInteger operationOrder;
  private final byte[] operationHead;
  private final List<String> findings;

  AttestationReceiptVerificationResult(
      UUID bookId, BigInteger operationOrder, byte[] operationHead, List<String> findings) {
    this.bookId = Objects.requireNonNull(bookId, "bookId");
    this.operationOrder = Objects.requireNonNull(operationOrder, "operationOrder");
    this.operationHead =
        Arrays.copyOf(Objects.requireNonNull(operationHead, "operationHead"), operationHead.length);
    if (this.operationHead.length != 32) {
      throw new IllegalArgumentException("operationHead must contain exactly 32 bytes.");
    }
    this.findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }

  /** Returns the book identity named by the verified receipt. */
  public UUID bookId() {
    return bookId;
  }

  /** Returns the authenticated operation position anchored by the receipt. */
  public BigInteger operationOrder() {
    return operationOrder;
  }

  /** Returns the authenticated operation head anchored by the receipt. */
  public byte[] operationHead() {
    return operationHead.clone();
  }

  /** Returns operational findings such as non-independent receipt retention. */
  public List<String> findings() {
    return findings;
  }
}
