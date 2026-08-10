package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionExecutionException;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Executes one receipt publication through destination admission and the transaction owner. */
final class AttestationReceiptPublicationFlow {
  private static final String RECEIPT_MEMBER_ID = "attestation-receipt";
  private static final String PUBLICATION_FAILURE_HINT =
      "Choose a writable output directory on a filesystem supporting atomic no-clobber"
          + " publication.";

  private final Path receiptPath;
  private final byte[] receipt;
  private final Path bookPath;
  private final AttestationVerification verification;
  private final AttestationReceiptPublicationOperations.ReceiptTransactionServiceFactory
      transactionServiceFactory;
  private final ReceiptArtifactPathAccess pathAccess;

  AttestationReceiptPublicationFlow(
      Path receiptPath,
      byte[] receipt,
      Path bookPath,
      AttestationVerification verification,
      AttestationReceiptPublicationOperations.ReceiptTransactionServiceFactory
          transactionServiceFactory,
      ReceiptArtifactPathAccess pathAccess) {
    this.receiptPath = receiptPath;
    this.receipt = Objects.requireNonNull(receipt, "receipt").clone();
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath");
    this.verification = Objects.requireNonNull(verification, "verification");
    this.transactionServiceFactory =
        Objects.requireNonNull(transactionServiceFactory, "transactionServiceFactory");
    this.pathAccess = Objects.requireNonNull(pathAccess, "pathAccess");
  }

  ContractDecision<ExportAttestationReceiptResult> publish() {
    Path requestedReceiptPath =
        Objects.requireNonNull(receiptPath, "receiptPath").toAbsolutePath().normalize();
    Path parent = requestedReceiptPath.getParent();
    Path canonicalParent;
    Path canonicalReceiptPath;
    try {
      if (parent == null || !pathAccess.isDirectoryNoFollow(parent)) {
        return invalidOutputDirectory(requestedReceiptPath);
      }
      PrivateOutputDirectory.requireExistingOwnerOnly(parent);
      canonicalParent = pathAccess.toRealPath(parent);
      PrivateOutputDirectory.requireExistingOwnerOnly(canonicalParent);
      canonicalReceiptPath =
          canonicalParent.resolve(
              Objects.requireNonNull(requestedReceiptPath.getFileName(), "receipt file name"));
    } catch (PrivateOutputDirectory.Violation exception) {
      return invalidOutputDirectory(requestedReceiptPath);
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return publicationFailure(requestedReceiptPath);
    }
    return publishReceipt(canonicalReceiptPath);
  }

  private ContractDecision<ExportAttestationReceiptResult> publishReceipt(
      Path canonicalReceiptPath) {
    try {
      PublicationTransactionService transactions = transactionServiceFactory.open();
      PublicationTransactionArtifact publication =
          new PublicationTransactionArtifact(
              canonicalReceiptPath,
              transactions.publish(
                  new PublicationTransactionRequest(
                      List.of(
                          new PublicationTransactionMemberRequest(
                              RECEIPT_MEMBER_ID,
                              PublicationTransactionMemberRole.ATTESTATION_RECEIPT,
                              canonicalReceiptPath,
                              PublicationMode.NO_REPLACE_LINK,
                              receipt)))));
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.Exported(
              publication,
              verification.bookId(),
              verification.headOrder(),
              HexFormat.of().formatHex(verification.operationHead()),
              AttestationReceiptPublicationOperations.publicationWarnings(
                  bookPath, publication.publishedArtifactPath())));
    } catch (PublicationTransactionExecutionException exception) {
      return ContractDecision.rejected(
          ContractErrors.publicationTransactionIncompleteFailure(
              canonicalReceiptPath, exception.result(), "--receipt-file"));
    } catch (IOException | RuntimeException exception) {
      return publicationFailure(canonicalReceiptPath);
    }
  }

  private static ContractDecision<ExportAttestationReceiptResult> publicationFailure(
      Path receiptPath) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
            receiptPath,
            "FinGrind could not publish the receipt artifact atomically.",
            PUBLICATION_FAILURE_HINT,
            "--receipt-file"));
  }

  private static ContractDecision<ExportAttestationReceiptResult> invalidOutputDirectory(
      Path receiptPath) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.failureAt(
            receiptPath,
            "The receipt output parent must be an existing real private directory whose resolved"
                + " ancestry resists non-owner substitution.",
            "Choose an existing private output directory with secure resolved ancestry for"
                + " --receipt-file, then rerun the command.",
            "--receipt-file"));
  }
}
