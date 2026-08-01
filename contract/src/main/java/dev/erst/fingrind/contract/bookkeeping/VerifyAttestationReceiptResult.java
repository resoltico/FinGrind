package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Non-mutating result family for verification of an independently retained receipt. */
public sealed interface VerifyAttestationReceiptResult
    permits VerifyAttestationReceiptResult.Valid, VerifyAttestationReceiptResult.Invalid {

  /**
   * Verified receipt bound to the supplied book's immutable chain, exact anchor head, and resolved
   * canonical physical artifact location.
   */
  record Valid(
      Path receiptFilePath,
      UUID bookId,
      BigInteger operationOrder,
      String operationHeadHex,
      List<String> findings)
      implements VerifyAttestationReceiptResult {
    public Valid {
      receiptFilePath =
          Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(operationOrder, "operationOrder");
      if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireOperationHeadHex(operationHeadHex);
      findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }
  }

  /** First exact receipt or underlying-chain structural failure. */
  record Invalid(String failureCode) implements VerifyAttestationReceiptResult {
    public Invalid {
      failureCode =
          AttestationVerificationFailure.requireVerificationWireCode(
              failureCode, OperationId.VERIFY_RECEIPT);
    }
  }

  private static String requireOperationHeadHex(String operationHeadHex) {
    String value = Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
