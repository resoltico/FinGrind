package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
import java.util.HexFormat;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps thrown CLI exceptions onto deterministic public failure envelopes. */
final class CliFailureMapper {
  private CliFailureMapper() {}

  static CliFailure cliFailure(CliCommandException exception) {
    return switch (Objects.requireNonNull(exception, "exception")) {
      case CliArgumentsException cliArgumentsException -> cliArgumentsException.failure();
      case CliRequestException cliRequestException -> cliRequestException.failure();
    };
  }

  static CliFailure contractFailure(ContractFailure failure) {
    return CliFailure.fromContractFailure(Objects.requireNonNull(failure, "failure"));
  }

  static @Nullable CliFailure runtimeFailure(RuntimeException exception) {
    return runtimeFailure(exception, null);
  }

  static @Nullable CliFailure runtimeFailure(RuntimeException exception, @Nullable String errorId) {
    CliFailure explicitFailure = explicitRuntimeFailure(exception);
    return explicitFailure != null ? explicitFailure : classifiedRuntimeFailure(exception, errorId);
  }

  private static @Nullable CliFailure explicitRuntimeFailure(RuntimeException exception) {
    if (exception instanceof AttestationStaleHeadException staleHead) {
      return new CliFailure(
          ContractErrors.Descriptor.STALE_HEAD.code(),
          "The attestation head advanced after this operation was signed, so FinGrind did not"
              + " persist it.",
          "Reload the current book state, re-sign the operation against the reported current head,"
              + " and retry.",
          null,
          new dev.erst.fingrind.cli.json.CliErrorJsonModels.StaleHeadDetails(
              HexFormat.of().formatHex(staleHead.observedHead()),
              HexFormat.of().formatHex(staleHead.currentHead()),
              staleHead.currentOrder().toString()));
    }
    if (exception instanceof CliArtifactOutputExistsException outputExistsException) {
      return CliFailure.fromContractFailure(
          ContractErrors.withRetainedArtifactStage(
              ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS.failureAt(
                  outputExistsException.outputPath(),
                  "The requested artifact destination already exists and will not be overwritten.",
                  "Choose a missing "
                      + outputExistsException.artifactOptionName()
                      + " destination or remove the existing artifact before rerunning the command.",
                  outputExistsException.artifactOptionName()),
              outputExistsException.retainedStage()));
    }
    if (exception instanceof CliArtifactOutputDirectoryException outputDirectoryException) {
      return new CliFailure(
          ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.code(),
          "The "
              + outputDirectoryException.artifactLabel()
              + " output parent must be an existing real private directory whose resolved ancestry"
              + " resists non-owner substitution.",
          "Choose an existing private output directory with secure resolved ancestry for "
              + outputDirectoryException.artifactOptionName()
              + ", then rerun the command.",
          outputDirectoryException.artifactOptionName(),
          outputDirectoryException.outputPath());
    }
    if (exception instanceof CliPdfPublicationDurabilityException durabilityException) {
      return CliFailure.fromContractFailure(
          ContractErrors.artifactPublicationDurabilityUncertainFailure(
              durabilityException.publication(), "--pdf-out"));
    }
    if (exception instanceof CliPdfExportException pdfExportException) {
      return pdfExportFailure(pdfExportException);
    }
    return null;
  }

  private static CliFailure pdfExportFailure(CliPdfExportException exception) {
    if (exception.getCause()
        instanceof dev.erst.fingrind.core.PublicationTransactionExecutionException interrupted) {
      return new CliFailure(
          ContractErrors.Descriptor.PDF_EXPORT_FAILURE.code(),
          "FinGrind could not complete the PDF publication transaction.",
          "Inspect the reported publication transaction and recover only with its identifier; do"
              + " not alter the candidate artifact or any private output directory.",
          "--pdf-out",
          new dev.erst.fingrind.cli.json.CliMaintenanceErrorJsonModels
              .PublicationTransactionIncompleteDetails(
              exception.outputPath().toString(),
              CliEnvelopeMapper.publicationTransaction(interrupted.result())),
          exception.outputPath(),
          java.util.List.of(),
          null);
    }
    if (exception.getCause()
        instanceof dev.erst.fingrind.core.ArtifactPublicationRetainedStageException retainedStage) {
      return CliFailure.fromContractFailure(
          ContractErrors.withRetainedArtifactStage(
              pdfExportPrimaryFailure(exception), retainedStage.retainedStage()));
    }
    if (exception.getCause()
        instanceof
        dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException outcomeUncertain) {
      return CliFailure.fromContractFailure(
          ContractErrors.artifactPublicationOutcomeUncertainFailure(
              outcomeUncertain.candidateArtifactPath(),
              outcomeUncertain.retainedStage(),
              "--pdf-out"));
    }
    return CliFailure.fromContractFailure(pdfExportPrimaryFailure(exception));
  }

  private static ContractFailure pdfExportPrimaryFailure(CliPdfExportException exception) {
    return ContractErrors.Descriptor.PDF_EXPORT_FAILURE.failureAt(
        exception.outputPath(),
        "Failed to write the PDF export.",
        "Inspect the selected --pdf-out destination, its parent directory permissions, and the"
            + " available filesystem space, then rerun the command.",
        "--pdf-out");
  }

  private static @Nullable CliFailure classifiedRuntimeFailure(
      RuntimeException exception, @Nullable String errorId) {
    return switch (SqliteFailureClassifier.classify(exception)) {
      case MANAGED_RUNTIME ->
          new CliFailure(
              ContractErrors.Descriptor.MANAGED_RUNTIME_FAILURE.code(),
              "The managed FinGrind runtime could not be verified.",
              "Run a supported FinGrind launcher surface: the extracted published Linux bundle"
                  + " launcher (bin/fingrind), the published container image, or from a local"
                  + " source checkout run ./gradlew :cli:prepareSourceCheckoutCliRuntime and rerun"
                  + " the generated launcher or developer direct-Java wrapper from that checkout.",
              null);
      case PERSISTENCE_INVARIANT ->
          errorId == null ? null : internalPersistenceInvariantError(errorId);
      case PROTECTED_BOOK_VERIFICATION ->
          CliFailure.fromContractFailure(ContractErrors.protectedBookVerificationFailure());
      case STORAGE ->
          new CliFailure(
              ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.code(),
              "The selected protected book could not be opened or updated.",
              "Inspect the selected book file path, chosen book passphrase source, initialization"
                  + " state, filesystem permissions, and the SQLite runtime message, then rerun"
                  + " after fixing the underlying storage problem.",
              null);
      case OTHER -> null;
    };
  }

  static CliFailure internalError(String errorId) {
    String normalizedErrorId = requireErrorId(errorId);
    return new CliFailure(
        ContractErrors.Descriptor.INTERNAL_ERROR.code(),
        "FinGrind encountered an internal error. Quote error id "
            + normalizedErrorId
            + " when reporting this defect.",
        internalErrorHint(null),
        null);
  }

  private static CliFailure internalPersistenceInvariantError(String errorId) {
    String normalizedErrorId = requireErrorId(errorId);
    return new CliFailure(
        ContractErrors.Descriptor.INTERNAL_ERROR.code(),
        "FinGrind encountered an internal persistence-contract breach. An upstream invariant should"
            + " have rejected this request before commit. Quote error id "
            + normalizedErrorId
            + " when reporting this defect.",
        internalErrorHint(
            "This failure class means a deterministic invariant leaked past pre-commit validation"
                + " into SQLite persistence."),
        null);
  }

  private static String internalErrorHint(@Nullable String additionalContext) {
    String baseHint =
        "FinGrind preserved the machine-readable error envelope on stderr and omitted raw stack"
            + " traces for this invocation. Quote the error id when reporting the defect; if you"
            + " need crash details locally, reproduce the failure against a disposable copy under a"
            + " debugger or test harness.";
    if (additionalContext == null) {
      return baseHint;
    }
    return additionalContext + " " + baseHint;
  }

  private static String requireErrorId(String errorId) {
    String normalized = Objects.requireNonNull(errorId, "errorId").strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("errorId must not be blank.");
    }
    return normalized;
  }
}
