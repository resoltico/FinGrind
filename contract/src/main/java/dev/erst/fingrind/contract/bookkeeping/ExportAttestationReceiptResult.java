package dev.erst.fingrind.contract.bookkeeping;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Closed outcome family for exporting one no-clobber, quorum-signed attestation receipt. */
public sealed interface ExportAttestationReceiptResult
    permits ExportAttestationReceiptResult.Exported,
        ExportAttestationReceiptResult.AuthorizationRejected {

  /** Successfully published one independently verifiable receipt artifact. */
  record Exported(
      Path receiptFilePath,
      UUID bookId,
      BigInteger operationOrder,
      String operationHeadHex,
      List<String> warnings)
      implements ExportAttestationReceiptResult {
    /** Validates the published receipt identity and its no-clobber publication warnings. */
    public Exported {
      receiptFilePath =
          Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
      Objects.requireNonNull(bookId, "bookId");
      Objects.requireNonNull(operationOrder, "operationOrder");
      if (operationOrder.signum() < 0 || operationOrder.bitLength() > Long.SIZE) {
        throw new IllegalArgumentException("operationOrder must be an unsigned 64-bit value.");
      }
      operationHeadHex = requireOperationHeadHex(operationHeadHex);
      warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
  }

  /** The current historical policy refused the selected receipt-signing credentials. */
  record AuthorizationRejected(AttestationVerificationFailure failure)
      implements ExportAttestationReceiptResult {
    /** Validates the exact historical authorization refusal. */
    public AuthorizationRejected {
      Objects.requireNonNull(failure, "failure");
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
