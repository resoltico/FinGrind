package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.util.Arrays;
import java.util.stream.Stream;

/** Owns the parser bindings for public book, attestation, and close lifecycle operations. */
final class CliLifecycleParserBindings {
  private CliLifecycleParserBindings() {}

  static CliCommandParsingRegistry.ParserBinding[] bindings() {
    return Stream.of(bookBindings(), attestationBindings(), closeBindings())
        .flatMap(Arrays::stream)
        .toArray(CliCommandParsingRegistry.ParserBinding[]::new);
  }

  private static CliCommandParsingRegistry.ParserBinding[] bookBindings() {
    return new CliCommandParsingRegistry.ParserBinding[] {
      CliCommandParsingRegistry.binding(
          OperationId.GENERATE_BOOK_KEY_FILE,
          CliLifecycleMutationArguments::parseGenerateBookKeyFileCommand),
      CliCommandParsingRegistry.binding(
          OperationId.OPEN_BOOK, CliLifecycleMutationArguments::parseOpenBookCommand),
      CliCommandParsingRegistry.binding(
          OperationId.REKEY_BOOK, CliLifecycleMutationArguments::parseRekeyBookCommand),
      CliCommandParsingRegistry.binding(
          OperationId.BACKUP_BOOK, CliLifecycleMutationArguments::parseBackupBookCommand),
      CliCommandParsingRegistry.binding(
          OperationId.RESTORE_BOOK, CliLifecycleMutationArguments::parseRestoreBookCommand)
    };
  }

  private static CliCommandParsingRegistry.ParserBinding[] attestationBindings() {
    return new CliCommandParsingRegistry.ParserBinding[] {
      CliCommandParsingRegistry.binding(
          OperationId.ENROLL_KEY, CliLifecycleMutationArguments::parseEnrollKeyCommand),
      CliCommandParsingRegistry.binding(
          OperationId.ROLLOVER_KEY, CliLifecycleMutationArguments::parseRolloverKeyCommand),
      CliCommandParsingRegistry.binding(
          OperationId.REVOKE_KEY, CliLifecycleMutationArguments::parseRevokeKeyCommand),
      CliCommandParsingRegistry.binding(
          OperationId.ALTER_POLICY, CliLifecycleMutationArguments::parseAlterPolicyCommand)
    };
  }

  private static CliCommandParsingRegistry.ParserBinding[] closeBindings() {
    return new CliCommandParsingRegistry.ParserBinding[] {
      CliCommandParsingRegistry.binding(
          OperationId.INTERIM_RESULT_SWEEP,
          CliLifecycleMutationArguments::parseInterimResultSweepCommand),
      CliCommandParsingRegistry.binding(
          OperationId.FISCAL_YEAR_CLOSE, CliLifecycleMutationArguments::parseFiscalYearCloseCommand)
    };
  }
}
