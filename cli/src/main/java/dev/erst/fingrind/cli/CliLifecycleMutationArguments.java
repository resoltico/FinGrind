package dev.erst.fingrind.cli;

import java.util.List;

/** Routes lifecycle-style mutation command parsing to focused command-family parsers. */
final class CliLifecycleMutationArguments {
  private CliLifecycleMutationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    return CliBookKeyGenerationArguments.parseGenerateBookKeyFileCommand(arguments);
  }

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    return CliOpenBookArguments.parseOpenBookCommand(arguments);
  }

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    return CliRekeyBookArguments.parseRekeyBookCommand(arguments);
  }

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    return CliBackupRestoreArguments.parseBackupBookCommand(arguments);
  }

  static CliCommand parseRestoreBookCommand(List<String> arguments) {
    return CliBackupRestoreArguments.parseRestoreBookCommand(arguments);
  }

  static CliCommand parseInspectRekeyRollbackCommand(List<String> arguments) {
    return CliRekeyRollbackArguments.parseInspectRekeyRollbackCommand(arguments);
  }

  static CliCommand parseDeleteRekeyRollbackCommand(List<String> arguments) {
    return CliRekeyRollbackArguments.parseDeleteRekeyRollbackCommand(arguments);
  }

  static CliCommand parseRestoreRekeyRollbackCommand(List<String> arguments) {
    return CliRekeyRollbackArguments.parseRestoreRekeyRollbackCommand(arguments);
  }

  static CliCommand parsePeriodResultTransferCommand(List<String> arguments) {
    return CliPeriodResultTransferArguments.parsePeriodResultTransferCommand(arguments);
  }
}
