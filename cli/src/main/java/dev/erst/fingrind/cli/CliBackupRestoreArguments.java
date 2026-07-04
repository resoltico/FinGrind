package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for backup and restore maintenance commands. */
final class CliBackupRestoreArguments {
  private static final List<String> RESTORE_BOOK_OPTIONS =
      List.of(
          ProtocolOptions.BOOK_FILE,
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BACKUP_FILE,
          ProtocolOptions.BACKUP_KEY_FILE,
          ProtocolOptions.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec BACKUP_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.BACKUP_FILE, ProtocolOptions.BACKUP_KEY_FILE, ProtocolOptions.OUTPUT),
          List.of());

  private CliBackupRestoreArguments() {}

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, BACKUP_BOOK_ARGUMENTS);
    Path backupFilePath = null;
    Path backupBookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.BACKUP_FILE.equals(argument)) {
        if (backupFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_FILE, "Duplicate argument: " + ProtocolOptions.BACKUP_FILE);
        }
        backupFilePath =
            CliOptionValues.requirePathOptionValue(argumentIterator, ProtocolOptions.BACKUP_FILE);
      } else if (ProtocolOptions.BACKUP_KEY_FILE.equals(argument)) {
        if (backupBookKeyFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_KEY_FILE,
              "Duplicate argument: " + ProtocolOptions.BACKUP_KEY_FILE);
        }
        backupBookKeyFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolOptions.BACKUP_KEY_FILE);
      } else {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      }
    }
    if (backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_FILE,
          "A " + ProtocolOptions.BACKUP_FILE + " argument is required.");
    }
    if (backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_KEY_FILE,
          "A " + ProtocolOptions.BACKUP_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctBackupPaths(
        parsedArguments.bookAccess().bookFilePath(),
        parsedArguments.bookAccess().passphraseSource(),
        backupFilePath,
        backupBookKeyFilePath);
    return new BackupBook(
        parsedArguments.bookAccess(),
        backupFilePath,
        backupBookKeyFilePath,
        CliOptionModes.resolvedOutputMode(outputMode));
  }

  static CliCommand parseRestoreBookCommand(List<String> arguments) {
    RestoreBookArgumentValues argumentValues = new RestoreBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyRestoreBookArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    if (argumentValues.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (argumentValues.bookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "A " + ProtocolOptions.BOOK_KEY_FILE + " argument is required.");
    }
    if (argumentValues.backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_FILE,
          "A " + ProtocolOptions.BACKUP_FILE + " argument is required.");
    }
    if (argumentValues.backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_KEY_FILE,
          "A " + ProtocolOptions.BACKUP_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctRestorePaths(
        argumentValues.bookFilePath,
        argumentValues.bookKeyFilePath,
        argumentValues.backupFilePath,
        argumentValues.backupBookKeyFilePath);
    return new RestoreBook(
        argumentValues.bookFilePath,
        argumentValues.bookKeyFilePath,
        argumentValues.backupFilePath,
        argumentValues.backupBookKeyFilePath,
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static void applyRestoreBookArgument(
      RestoreBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.BOOK_FILE ->
          argumentValues.bookFilePath =
              requireSingleRestorePath(
                  argumentValues.bookFilePath, argumentIterator, ProtocolOptions.BOOK_FILE);
      case ProtocolOptions.BOOK_KEY_FILE ->
          argumentValues.bookKeyFilePath =
              requireSingleRestorePath(
                  argumentValues.bookKeyFilePath, argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
      case ProtocolOptions.BACKUP_FILE ->
          argumentValues.backupFilePath =
              requireSingleRestorePath(
                  argumentValues.backupFilePath, argumentIterator, ProtocolOptions.BACKUP_FILE);
      case ProtocolOptions.BACKUP_KEY_FILE ->
          argumentValues.backupBookKeyFilePath =
              requireSingleRestorePath(
                  argumentValues.backupBookKeyFilePath,
                  argumentIterator,
                  ProtocolOptions.BACKUP_KEY_FILE);
      case ProtocolOptions.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default -> throw CliArgumentValueParser.unsupportedArgument(argument, RESTORE_BOOK_OPTIONS);
    }
  }

  private static Path requireSingleRestorePath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  /** Mutable parse accumulator for restore-book command options before validation. */
  private static final class RestoreBookArgumentValues {
    private @Nullable Path bookFilePath;
    private @Nullable Path bookKeyFilePath;
    private @Nullable Path backupFilePath;
    private @Nullable Path backupBookKeyFilePath;
    private @Nullable OutputMode outputMode;
  }
}
