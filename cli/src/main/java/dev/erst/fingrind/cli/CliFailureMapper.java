package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
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
    if (exception instanceof CliArtifactOutputExistsException outputExistsException) {
      return new CliFailure(
          ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS.code(),
          message(outputExistsException),
          "Choose one missing "
              + outputExistsException.artifactOptionName()
              + " destination or remove the existing artifact before rerunning the command.",
          outputExistsException.artifactOptionName());
    }
    if (exception instanceof CliPdfExportException pdfExportException) {
      return new CliFailure(
          ContractErrors.Descriptor.PDF_EXPORT_FAILURE.code(),
          message(pdfExportException),
          "Inspect the selected --pdf-out destination, its parent directory permissions, and the available filesystem space, then rerun the command.",
          "--pdf-out");
    }
    String message = message(exception);
    return switch (SqliteFailureClassifier.classify(exception)) {
      case MANAGED_RUNTIME ->
          new CliFailure(
              ContractErrors.Descriptor.MANAGED_RUNTIME_FAILURE.code(),
              message,
              "Run a supported FinGrind launcher surface: the extracted published Linux bundle launcher (bin/fingrind), the published container image, or from a local source checkout run ./gradlew :cli:prepareSourceCheckoutCliRuntime and rerun the generated launcher or developer direct-Java wrapper from that checkout.",
              null);
      case STORAGE ->
          new CliFailure(
              ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.code(),
              message,
              "Inspect the selected book file path, chosen book passphrase source, initialization state, filesystem permissions, and the SQLite runtime message, then rerun after fixing the underlying storage problem.",
              null);
      case OTHER -> null;
    };
  }

  static CliFailure internalError(String errorId, OutputMode outputMode) {
    String normalizedErrorId = requireErrorId(errorId);
    return new CliFailure(
        ContractErrors.Descriptor.INTERNAL_ERROR.code(),
        "FinGrind encountered an internal error. Quote error id "
            + normalizedErrorId
            + " when reporting this defect.",
        internalErrorHint(Objects.requireNonNull(outputMode, "outputMode")),
        null);
  }

  private static String internalErrorHint(OutputMode outputMode) {
    if (outputMode == OutputMode.TEXT) {
      return "Inspect the diagnostic stream for the same error id and stack trace, then report the defect instead of retrying unchanged input.";
    }
    return "FinGrind preserved one machine-readable error envelope on stderr and omitted the raw stack trace for this invocation. Quote the error id when reporting the defect; if you need local crash details, reproduce the failure against a disposable copy with --output text.";
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }

  private static String requireErrorId(String errorId) {
    String normalized = Objects.requireNonNull(errorId, "errorId").strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("errorId must not be blank.");
    }
    return normalized;
  }
}
