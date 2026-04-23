package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
import dev.erst.fingrind.sqlite.SqliteFailureClassifier;
import java.util.Objects;

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

  static CliFailure runtimeFailure(RuntimeException exception) {
    if (exception instanceof ContractFailureException contractFailureException) {
      return contractFailure(contractFailureException.failure());
    }
    if (exception instanceof CliPdfExportException pdfExportException) {
      return new CliFailure(
          ContractErrors.Descriptor.PDF_EXPORT_FAILURE.code(),
          message(pdfExportException),
          "Inspect the selected --pdf-out destination, its parent directory permissions, and the available filesystem space, then rerun the command.",
          "--pdf-out");
    }
    String message = message(exception);
    String hint =
        switch (SqliteFailureClassifier.classify(exception)) {
          case MANAGED_RUNTIME ->
              "Run the published FinGrind bundle launcher (bin/fingrind on macOS/Linux or bin\\fingrind.ps1 on Windows), or for a local source checkout build the managed SQLite runtime with ./gradlew prepareManagedSqlite and set FINGRIND_SQLITE_LIBRARY before rerunning.";
          case STORAGE ->
              "Inspect the selected book file path, chosen book passphrase source, initialization state, filesystem permissions, and the SQLite runtime message, then rerun after fixing the underlying storage problem.";
          case OTHER ->
              "Inspect the message and rerun after fixing the underlying runtime problem.";
        };
    ContractErrors.Descriptor descriptor =
        switch (SqliteFailureClassifier.classify(exception)) {
          case MANAGED_RUNTIME -> ContractErrors.Descriptor.MANAGED_RUNTIME_FAILURE;
          case STORAGE -> ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE;
          case OTHER -> ContractErrors.Descriptor.RUNTIME_FAILURE;
        };
    return new CliFailure(descriptor.code(), message, hint, null);
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }
}
