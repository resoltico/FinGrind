package dev.erst.fingrind.cli;

import java.util.List;

/** Parses CLI commands that mutate one selected book or generate secrets. */
final class CliMutationArguments {
  private CliMutationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseGenerateBookKeyFileCommand(arguments);
  }

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseOpenBookCommand(arguments);
  }

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseBackupBookCommand(arguments);
  }

  static CliCommand parseRestoreBookCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseRestoreBookCommand(arguments);
  }

  static CliCommand parseInspectRekeyRollbackCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseInspectRekeyRollbackCommand(arguments);
  }

  static CliCommand parseDeleteRekeyRollbackCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseDeleteRekeyRollbackCommand(arguments);
  }

  static CliCommand parseRestoreRekeyRollbackCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseRestoreRekeyRollbackCommand(arguments);
  }

  static CliCommand parseDeclareAccountCommand(List<String> arguments) {
    return CliRequestMutationArguments.parseDeclareAccountCommand(arguments);
  }

  static CliCommand parsePeriodResultTransferCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parsePeriodResultTransferCommand(arguments);
  }

  static CliCommand parseExecutePlanCommand(List<String> arguments) {
    return CliRequestMutationArguments.parseExecutePlanCommand(arguments);
  }

  static CliCommand parsePreflightEntryCommand(List<String> arguments) {
    return CliRequestMutationArguments.parsePreflightEntryCommand(arguments);
  }

  static CliCommand parsePostEntryCommand(List<String> arguments) {
    return CliRequestMutationArguments.parsePostEntryCommand(arguments);
  }

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    return CliLifecycleMutationArguments.parseRekeyBookCommand(arguments);
  }
}
