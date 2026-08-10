package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes one receipt through the sole transaction owner without replacing an existing output.
 */
final class AttestationReceiptPublicationOperations {
  private AttestationReceiptPublicationOperations() {}

  static ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath, byte[] receipt, Path bookPath, AttestationVerification verification) {
    return publish(
        receiptPath,
        receipt,
        bookPath,
        verification,
        PublicationTransactionPublisher::openCanonical,
        ReceiptArtifactPathAccess.FILE_SYSTEM);
  }

  static ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath,
      byte[] receipt,
      Path bookPath,
      AttestationVerification verification,
      ReceiptTransactionServiceFactory transactionServiceFactory,
      ReceiptArtifactPathAccess pathAccess) {
    return new AttestationReceiptPublicationFlow(
            receiptPath, receipt, bookPath, verification, transactionServiceFactory, pathAccess)
        .publish();
  }

  static List<String> publicationWarnings(Path bookPath, Path receiptPath) {
    List<String> warnings = new ArrayList<>();
    if (AttestationReceiptVerificationOperations.publicationReceiptRetention(bookPath, receiptPath)
        == AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY) {
      warnings.add("receipt-not-independent");
    }
    return List.copyOf(warnings);
  }

  /** Opens the transaction authority without granting a receipt publisher filesystem primitives. */
  @FunctionalInterface
  interface ReceiptTransactionServiceFactory {
    /** Opens the only service allowed to stage, commit, clean, or recover receipt publication. */
    PublicationTransactionService open() throws IOException;
  }
}
