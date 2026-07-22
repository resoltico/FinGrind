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

  static CliCommand parseEnrollKeyCommand(List<String> arguments) {
    return CliAttestationRegistryMutationArguments.parseEnrollKeyCommand(arguments);
  }

  static CliCommand parseRolloverKeyCommand(List<String> arguments) {
    return CliAttestationRegistryMutationArguments.parseRolloverKeyCommand(arguments);
  }

  static CliCommand parseRevokeKeyCommand(List<String> arguments) {
    return CliAttestationRegistryMutationArguments.parseRevokeKeyCommand(arguments);
  }

  static CliCommand parseAlterPolicyCommand(List<String> arguments) {
    return CliAttestationRegistryMutationArguments.parseAlterPolicyCommand(arguments);
  }

  static CliCommand parseInterimResultSweepCommand(List<String> arguments) {
    return CliInterimResultSweepArguments.parseInterimResultSweepCommand(arguments);
  }

  static CliCommand parseFiscalYearCloseCommand(List<String> arguments) {
    return CliFiscalYearCloseArguments.parseFiscalYearCloseCommand(arguments);
  }
}
