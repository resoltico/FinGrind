package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetainedStageException;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.PrivateOutputDirectory;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Executes one receipt publication through destination admission, staging, linking, and forcing.
 */
final class AttestationReceiptPublicationFlow {
  private static final String PUBLICATION_FAILURE_HINT =
      "Choose a writable output directory on a filesystem supporting atomic no-clobber"
          + " publication.";

  private final Path receiptPath;
  private final byte[] receipt;
  private final Path bookPath;
  private final AttestationVerification verification;
  private final AttestationReceiptPublicationOperations.ReceiptNoReplaceLinkCreator
      noReplaceLinkCreator;
  private final AttestationReceiptPublicationOperations.ReceiptDirectoryDurabilityForcer
      directoryDurabilityForcer;
  private final ReceiptArtifactPathAccess pathAccess;
  private final AttestationReceiptPublicationOperations.ReceiptStageFileOperations
      stageFileOperations;

  AttestationReceiptPublicationFlow(
      Path receiptPath,
      byte[] receipt,
      Path bookPath,
      AttestationVerification verification,
      AttestationReceiptPublicationOperations.ReceiptNoReplaceLinkCreator noReplaceLinkCreator,
      AttestationReceiptPublicationOperations.ReceiptDirectoryDurabilityForcer
          directoryDurabilityForcer,
      ReceiptArtifactPathAccess pathAccess,
      AttestationReceiptPublicationOperations.ReceiptStageFileOperations stageFileOperations) {
    this.receiptPath = receiptPath;
    this.receipt = Objects.requireNonNull(receipt, "receipt").clone();
    this.bookPath = bookPath;
    this.verification = verification;
    this.noReplaceLinkCreator =
        Objects.requireNonNull(noReplaceLinkCreator, "noReplaceLinkCreator");
    this.directoryDurabilityForcer =
        Objects.requireNonNull(directoryDurabilityForcer, "directoryDurabilityForcer");
    this.pathAccess = Objects.requireNonNull(pathAccess, "pathAccess");
    this.stageFileOperations = Objects.requireNonNull(stageFileOperations, "stageFileOperations");
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
      canonicalParent = pathAccess.toRealPath(parent);
      canonicalReceiptPath =
          canonicalParent.resolve(
              Objects.requireNonNull(requestedReceiptPath.getFileName(), "receipt file name"));
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return publicationFailure(requestedReceiptPath);
    }
    try {
      PrivateOutputDirectory.requireExistingOwnerOnly(canonicalParent);
    } catch (PrivateOutputDirectory.Violation exception) {
      return invalidOutputDirectory(canonicalReceiptPath);
    }
    return publishStagedReceipt(canonicalParent, canonicalReceiptPath);
  }

  private ContractDecision<ExportAttestationReceiptResult> publishStagedReceipt(
      Path canonicalParent, Path canonicalReceiptPath) {
    Path stagedPath;
    try {
      stagedPath = stageFileOperations.createAndWrite(canonicalParent, receipt);
    } catch (ArtifactPublicationRetainedStageException exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              publicationFailure(canonicalReceiptPath).requireRejected(),
              exception.retainedStage()));
    } catch (IOException | RuntimeException exception) {
      return publicationFailure(canonicalReceiptPath);
    }
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(stagedPath);
    try {
      noReplaceLinkCreator.create(canonicalReceiptPath, stagedPath);
    } catch (FileAlreadyExistsException exception) {
      return ContractDecision.rejected(
          ContractErrors.withRetainedArtifactStage(
              outputAlreadyExistsFailure(canonicalReceiptPath), retention));
    } catch (IOException | RuntimeException exception) {
      return ContractDecision.rejected(
          ContractErrors.artifactPublicationOutcomeUncertainFailure(
              canonicalReceiptPath, retention, "--receipt-file"));
    }
    try {
      directoryDurabilityForcer.force(canonicalParent);
    } catch (IOException | RuntimeException exception) {
      return ContractDecision.rejected(
          ContractErrors.artifactPublicationDurabilityUncertainFailure(
              new ArtifactPublicationResult(canonicalReceiptPath, retention), "--receipt-file"));
    }
    ArtifactPublicationResult publication =
        new ArtifactPublicationResult(canonicalReceiptPath, retention);
    return ContractDecision.accepted(
        new ExportAttestationReceiptResult.Exported(
            publication,
            verification.bookId(),
            verification.headOrder(),
            HexFormat.of().formatHex(verification.operationHead()),
            AttestationReceiptPublicationOperations.publicationWarnings(
                bookPath, publication.publishedArtifactPath())));
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

  private static ContractFailure outputAlreadyExistsFailure(Path receiptPath) {
    return ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS.failureAt(
        receiptPath,
        "The selected receipt output already exists and FinGrind will not overwrite it.",
        "Choose an absent --receipt-file path and rerun the command.",
        "--receipt-file");
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
