package dev.erst.fingrind.contract.bookkeeping;

import java.math.BigInteger;
import java.util.Objects;

/** Authenticated chain position created by one newly committed attested operation. */
public record AttestationCommit(BigInteger operationOrder, String operationHeadHex) {
  public AttestationCommit {
    Objects.requireNonNull(operationOrder, "operationOrder");
    if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
      throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
    }
    Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!operationHeadHex.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
  }
}
