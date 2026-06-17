package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import java.util.Optional;

/** Shared CLI execution policy for public failure exit codes and prompt restrictions. */
final class CliExecutionPolicy {
  private CliExecutionPolicy() {}

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
            ContractErrors.Descriptor.UNSUPPORTED_OUTPUT_SELECTION.failure(
                "Interactive passphrase prompting is only supported with --output text.",
                "Rerun with --output text, or switch the passphrase source to --book-key-file or --book-passphrase-stdin before selecting one machine output mode.",
                ProtocolOptions.OUTPUT));
      }
    }
    return Optional.empty();
  }
}
