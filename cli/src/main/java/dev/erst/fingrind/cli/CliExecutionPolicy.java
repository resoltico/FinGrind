package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Locale;
import java.util.Optional;

/** Shared CLI execution policy for failure-mode inference and public failure exit codes. */
final class CliExecutionPolicy {
  private CliExecutionPolicy() {}

  static OutputMode inferredFailureOutputMode(String[] args) {
    if (args.length == 0) {
      return OutputMode.TEXT;
    }
    OutputMode inferred = defaultFailureOutputMode(args[0]);
    int index = 1;
    while (index + 1 < args.length) {
      if (!ProtocolOptions.OUTPUT.equals(args[index])) {
        index++;
        continue;
      }
      Optional<OutputMode> parsedOutputMode = parseRecognizedOutputMode(args[index + 1]);
      if (parsedOutputMode.isPresent()) {
        inferred = parsedOutputMode.orElseThrow();
      }
      index += 2;
    }
    return inferred == OutputMode.TEXT ? OutputMode.TEXT : OutputMode.JSON;
  }

  private static OutputMode defaultFailureOutputMode(String commandToken) {
    String normalized = commandToken.toLowerCase(Locale.ROOT);
    if (OperationId.PRINT_REQUEST_TEMPLATE.wireName().equals(normalized)
        || OperationId.PRINT_PLAN_TEMPLATE.wireName().equals(normalized)) {
      return OutputMode.JSON;
    }
    return switch (normalized) {
      case "help", "version", "capabilities" -> CliOutputModeDefaults.defaultDiscoveryOutputMode();
      default -> CliOutputModeDefaults.defaultSelectableOutputMode();
    };
  }

  private static Optional<OutputMode> parseRecognizedOutputMode(String rawOutputMode) {
    for (OutputMode outputMode : OutputMode.values()) {
      if (outputMode.wireValue().equals(rawOutputMode)) {
        return Optional.of(outputMode);
      }
    }
    return Optional.empty();
  }

  static int invalidInvocationExitCode() {
    return 1;
  }

  static int contractFailureExitCode(ContractFailure failure) {
    return failure.descriptor().exitCode();
  }

  static int failureExitCode(CliFailure failure) {
    String failureCode = failure.code();
    for (ContractErrors.Descriptor descriptor : ContractErrors.Descriptor.values()) {
      if (descriptor.code().equals(failureCode)) {
        return descriptor.exitCode();
      }
    }
    return runtimeFailureExitCode();
  }

  static int runtimeFailureExitCode() {
    return 4;
  }

  static Optional<ContractFailure> interactivePromptOutputFailure(
      OutputMode outputMode, BookAccess.PassphraseSource... passphraseSources) {
    if (outputMode == OutputMode.TEXT) {
      return Optional.empty();
    }
    for (BookAccess.PassphraseSource passphraseSource : passphraseSources) {
      if (passphraseSource instanceof BookAccess.PassphraseSource.InteractivePrompt) {
        return Optional.of(
            ContractErrors.Descriptor.INVALID_REQUEST.failure(
                "Interactive passphrase prompting is only supported with --output text.",
                "Rerun with --output text, or switch the passphrase source to --book-key-file or --book-passphrase-stdin before selecting one machine output mode.",
                ProtocolOptions.OUTPUT));
      }
    }
    return Optional.empty();
  }
}
