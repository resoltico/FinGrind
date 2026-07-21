package dev.erst.fingrind.contract.bookkeeping;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Result of exporting one no-clobber, quorum-signed attestation receipt. */
public record ExportAttestationReceiptResult(
    Path receiptFilePath,
    UUID bookId,
    BigInteger operationOrder,
    String operationHeadHex,
    List<String> warnings) {
  public ExportAttestationReceiptResult {
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

  private static String requireOperationHeadHex(String operationHeadHex) {
    String value = Objects.requireNonNull(operationHeadHex, "operationHeadHex");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(
          "operationHeadHex must contain 64 lowercase hexadecimal characters.");
    }
    return value;
  }
}
