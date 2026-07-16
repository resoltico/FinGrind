package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for rekey-rollback inspection, restore, and deletion commands. */
final class CliRekeyRollbackArguments {
  private static final List<String> REKEY_ROLLBACK_OPTIONS =
      List.of(
          ProtocolBookAccessOptions.BOOK_FILE,
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
          ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT,
          ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE,
          ProtocolOptions.Presentation.OUTPUT);
  private static final String DELETE_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.DELETE_REKEY_ROLLBACK);
  private static final String INSPECT_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.INSPECT_REKEY_ROLLBACK);
  private static final String RESTORE_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.RESTORE_REKEY_ROLLBACK);

  private CliRekeyRollbackArguments() {}

  static CliCommand parseInspectRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    rejectUnexpectedRollbackPath(parsedArguments.rollbackArtifactPath());
    rejectUnexpectedPassphraseSource(parsedArguments.passphraseSourceKind());
    return new InspectRekeyRollback(
        parsedArguments.bookFilePath(),
        CliOptionModes.resolvedOutputMode(parsedArguments.outputMode()));
  }

  static CliCommand parseDeleteRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    if (parsedArguments.passphraseSourceKind() == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          "Delete rekey rollback requires exactly one book passphrase source: "
              + ProtocolBookAccessOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseParser.passphraseSource(
            parsedArguments.passphraseSourceKind(), parsedArguments.bookKeyFilePath());
    CliBookPathValidator.validateDistinctPaths(
        parsedArguments.bookFilePath(), passphraseSource, null);
    CliBookPathValidator.validateStandardInputUsage(passphraseSource, null);
    return new DeleteRekeyRollback(
        new BookAccess(parsedArguments.bookFilePath(), passphraseSource),
        parsedArguments.rollbackArtifactPath(),
        CliOptionModes.resolvedOutputMode(parsedArguments.outputMode()));
  }

  static CliCommand parseRestoreRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    if (parsedArguments.passphraseSourceKind() == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          "Restore rekey rollback requires exactly one book passphrase source: "
              + ProtocolBookAccessOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource expectedPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            parsedArguments.passphraseSourceKind(), parsedArguments.bookKeyFilePath());
    CliBookPathValidator.validateDistinctPaths(
        parsedArguments.bookFilePath(), expectedPassphraseSource, null);
    CliBookPathValidator.validateStandardInputUsage(expectedPassphraseSource, null);
    return new RestoreRekeyRollback(
        parsedArguments.bookFilePath(),
        parsedArguments.rollbackArtifactPath(),
        expectedPassphraseSource,
        CliOptionModes.resolvedOutputMode(parsedArguments.outputMode()));
  }

  private static ParsedRekeyRollbackArguments parseRekeyRollbackArguments(List<String> arguments) {
    ParsedRekeyRollbackArgumentValues argumentValues = new ParsedRekeyRollbackArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyRekeyRollbackArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    if (argumentValues.bookAccess.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_FILE,
          "A " + ProtocolBookAccessOptions.BOOK_FILE + " argument is required.");
    }
    return new ParsedRekeyRollbackArguments(
        argumentValues.bookAccess.bookFilePath,
        argumentValues.rollbackArtifactPath,
        argumentValues.bookAccess.bookKeyFilePath,
        argumentValues.bookAccess.passphraseSourceKind,
        argumentValues.bookAccess.outputMode);
  }

  private static void applyRekeyRollbackArgument(
      ParsedRekeyRollbackArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    if (CliMaintenanceBookAccessArguments.apply(
        argumentValues.bookAccess, argument, argumentIterator)) {
      return;
    }
    if (ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE.equals(argument)) {
      argumentValues.rollbackArtifactPath =
          CliMaintenanceBookAccessArguments.requireSinglePath(
              argumentValues.rollbackArtifactPath,
              argumentIterator,
              ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE);
      return;
    }
    throw CliArgumentValueParser.unsupportedArgument(argument, REKEY_ROLLBACK_OPTIONS);
  }

  /** Mutable parse accumulator for rekey rollback command options before validation. */
  private static final class ParsedRekeyRollbackArgumentValues {
    private final CliMaintenanceBookAccessArguments.Values bookAccess =
        new CliMaintenanceBookAccessArguments.Values();
    private @Nullable Path rollbackArtifactPath;
  }

  private static void rejectUnexpectedRollbackPath(@Nullable Path rollbackArtifactPath) {
    if (rollbackArtifactPath != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE,
          ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE
              + " is accepted only when "
              + DELETE_REKEY_ROLLBACK_COMMAND
              + " or "
              + RESTORE_REKEY_ROLLBACK_COMMAND
              + " is selected.");
    }
  }

  private static void rejectUnexpectedPassphraseSource(
      CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind) {
    if (passphraseSourceKind != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          INSPECT_REKEY_ROLLBACK_COMMAND
              + " inspects sibling rollback artifact paths without opening the protected book and therefore accepts no book passphrase source. "
              + DELETE_REKEY_ROLLBACK_COMMAND
              + " and "
              + RESTORE_REKEY_ROLLBACK_COMMAND
              + " require a source because they act on a selected rollback artifact.");
    }
  }

  private record ParsedRekeyRollbackArguments(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      @Nullable Path bookKeyFilePath,
      CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind,
      @Nullable OutputMode outputMode) {}
}
