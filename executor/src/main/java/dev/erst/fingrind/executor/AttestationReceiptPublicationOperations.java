package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.ArtifactPublicationStages;
import dev.erst.fingrind.core.PrivateOutputDirectoryDurability;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Publishes one receipt atomically without replacing an existing output artifact. */
final class AttestationReceiptPublicationOperations {
  private static final String STAGED_RECEIPT_PREFIX = ".fingrind-receipt-";
  private static final String STAGED_RECEIPT_SUFFIX = ".fgar";
  private static final ReceiptStageFileOperations FILE_SYSTEM_STAGE_FILE_OPERATIONS =
      (parentDirectory, receipt) ->
          ArtifactPublicationStages.createAndWrite(
              parentDirectory, STAGED_RECEIPT_PREFIX, STAGED_RECEIPT_SUFFIX, receipt);

  private AttestationReceiptPublicationOperations() {}

  static ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath, byte[] receipt, Path bookPath, AttestationVerification verification) {
    return publish(
        receiptPath,
        receipt,
        bookPath,
        verification,
        Files::createLink,
        AttestationReceiptPublicationOperations::forceDirectory,
        ReceiptArtifactPathAccess.FILE_SYSTEM,
        FILE_SYSTEM_STAGE_FILE_OPERATIONS);
  }

  static ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath,
      byte[] receipt,
      Path bookPath,
      AttestationVerification verification,
      ReceiptNoReplaceLinkCreator noReplaceLinkCreator,
      ReceiptDirectoryDurabilityForcer directoryDurabilityForcer,
      ReceiptArtifactPathAccess pathAccess,
      ReceiptStageFileOperations stageFileOperations) {
    return new AttestationReceiptPublicationFlow(
            receiptPath,
            receipt,
            bookPath,
            verification,
            noReplaceLinkCreator,
            directoryDurabilityForcer,
            pathAccess,
            stageFileOperations)
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

  private static void forceDirectory(Path directory) throws IOException {
    PrivateOutputDirectoryDurability.force(directory);
  }

  /** Creates one final receipt name without replacing an existing artifact. */
  @FunctionalInterface
  interface ReceiptNoReplaceLinkCreator {
    /** Creates the final receipt name as a link to the retained staged receipt artifact. */
    void create(Path finalPath, Path stagedPath) throws IOException;
  }

  /** Forces the directory containing a newly published receipt name. */
  @FunctionalInterface
  interface ReceiptDirectoryDurabilityForcer {
    /** Persists the selected directory's receipt-name mutation. */
    void force(Path directory) throws IOException;
  }

  /** Creates and force-writes the owner-private staged receipt through one exact channel. */
  @FunctionalInterface
  interface ReceiptStageFileOperations {
    /** Creates a fresh retained receipt stage and forces its completed bytes. */
    Path createAndWrite(Path parentDirectory, byte[] receipt) throws IOException;
  }
}
