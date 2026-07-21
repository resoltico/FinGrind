package dev.erst.fingrind.contract.bookkeeping;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating result family for verification of an independently retained receipt. */
public sealed interface VerifyAttestationReceiptResult
    permits VerifyAttestationReceiptResult.Valid, VerifyAttestationReceiptResult.Invalid {

  /** Verified receipt bound to the supplied book's immutable chain. */
  record Valid(UUID bookId, BigInteger operationOrder, List<String> findings)
      implements VerifyAttestationReceiptResult {
    public Valid {
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(operationOrder, "operationOrder");
      if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
      }
      findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
  }

  /** First exact receipt or underlying-chain structural failure. */
  record Invalid(String failureCode) implements VerifyAttestationReceiptResult {
    public Invalid {
      failureCode = Objects.requireNonNull(failureCode, "failureCode").strip();
      if (failureCode.isEmpty()) {
        throw new IllegalArgumentException("failureCode must not be blank.");
      }
    }
  }
}
