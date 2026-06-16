package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
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
          ProtocolOptions.BOOK_FILE,
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BOOK_PASSPHRASE_STDIN,
          ProtocolOptions.BOOK_PASSPHRASE_PROMPT,
          ProtocolOptions.ROLLBACK_BOOK_FILE,
          ProtocolOptions.OUTPUT);
  private static final String DELETE_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.DELETE_REKEY_ROLLBACK);
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
          ProtocolOptions.BOOK_KEY_FILE,
          "Delete rekey rollback requires exactly one book passphrase source: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
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
          ProtocolOptions.BOOK_KEY_FILE,
          "Restore rekey rollback requires exactly one book passphrase source: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
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
    if (argumentValues.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    return new ParsedRekeyRollbackArguments(
        argumentValues.bookFilePath,
        argumentValues.rollbackArtifactPath,
        argumentValues.bookKeyFilePath,
        argumentValues.passphraseSourceKind,
        argumentValues.outputMode);
  }

  private static void applyRekeyRollbackArgument(
      ParsedRekeyRollbackArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.BOOK_FILE ->
          argumentValues.bookFilePath =
              requireSingleRollbackPath(
                  argumentValues.bookFilePath, argumentIterator, ProtocolOptions.BOOK_FILE);
      case ProtocolOptions.BOOK_KEY_FILE -> {
        argumentValues.passphraseSourceKind =
            CliBookPassphraseParser.requireSinglePassphraseSource(
                argumentValues.passphraseSourceKind,
                CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
        argumentValues.bookKeyFilePath =
            CliOptionValues.requirePathOptionValue(argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
      }
      case ProtocolOptions.BOOK_PASSPHRASE_STDIN ->
          argumentValues.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
      case ProtocolOptions.BOOK_PASSPHRASE_PROMPT ->
          argumentValues.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
      case ProtocolOptions.ROLLBACK_BOOK_FILE ->
          argumentValues.rollbackArtifactPath =
              requireSingleRollbackPath(
                  argumentValues.rollbackArtifactPath,
                  argumentIterator,
                  ProtocolOptions.ROLLBACK_BOOK_FILE);
      case ProtocolOptions.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default -> throw CliArgumentValueParser.unsupportedArgument(argument, REKEY_ROLLBACK_OPTIONS);
    }
  }

  private static Path requireSingleRollbackPath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  /** Mutable parse accumulator for rekey rollback command options before validation. */
  private static final class ParsedRekeyRollbackArgumentValues {
    private @Nullable Path bookFilePath;
    private @Nullable Path rollbackArtifactPath;
    private @Nullable Path bookKeyFilePath;
    private CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind;
    private @Nullable OutputMode outputMode;
  }

  private static void rejectUnexpectedRollbackPath(@Nullable Path rollbackArtifactPath) {
    if (rollbackArtifactPath != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ROLLBACK_BOOK_FILE,
          ProtocolOptions.ROLLBACK_BOOK_FILE
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
          ProtocolOptions.BOOK_KEY_FILE,
          "Book passphrase source arguments are accepted only when "
              + DELETE_REKEY_ROLLBACK_COMMAND
              + " or "
              + RESTORE_REKEY_ROLLBACK_COMMAND
              + " is selected.");
    }
  }

  private record ParsedRekeyRollbackArguments(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      @Nullable Path bookKeyFilePath,
      CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind,
      @Nullable OutputMode outputMode) {}
}
