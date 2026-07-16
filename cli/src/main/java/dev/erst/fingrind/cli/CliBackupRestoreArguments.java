package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for backup and restore maintenance commands. */
final class CliBackupRestoreArguments {
  private static final List<String> RESTORE_BOOK_OPTIONS =
      List.of(
          ProtocolBookAccessOptions.BOOK_FILE,
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          ProtocolBookAccessOptions.BACKUP_FILE,
          ProtocolBookAccessOptions.BACKUP_KEY_FILE,
          ProtocolBookAccessOptions.REPLACE_EXISTING_BOOK,
          ProtocolOptions.Presentation.OUTPUT);
  private static final CliBookArgumentParser.CommandArgumentSpec BACKUP_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolBookAccessOptions.BACKUP_FILE,
              ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
              ProtocolOptions.Presentation.OUTPUT),
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
      if (ProtocolBookAccessOptions.BACKUP_FILE.equals(argument)) {
        if (backupFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolBookAccessOptions.BACKUP_FILE,
              "Duplicate argument: " + ProtocolBookAccessOptions.BACKUP_FILE);
        }
        backupFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolBookAccessOptions.BACKUP_FILE);
      } else if (ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE.equals(argument)) {
        if (backupBookKeyFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
              "Duplicate argument: " + ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
        }
        backupBookKeyFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
      } else {
        outputMode =
            CliOptionModes.requireOutputMode(
                outputMode,
                CliOptionValues.requireValue(argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      }
    }
    if (backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BACKUP_FILE,
          "A " + ProtocolBookAccessOptions.BACKUP_FILE + " argument is required.");
    }
    if (backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
          "A " + ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE + " argument is required.");
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
          ProtocolBookAccessOptions.BOOK_FILE,
          "A " + ProtocolBookAccessOptions.BOOK_FILE + " argument is required.");
    }
    if (argumentValues.newBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          "A " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " argument is required.");
    }
    if (argumentValues.backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BACKUP_FILE,
          "A " + ProtocolBookAccessOptions.BACKUP_FILE + " argument is required.");
    }
    if (argumentValues.backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BACKUP_KEY_FILE,
          "A " + ProtocolBookAccessOptions.BACKUP_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctRestorePaths(
        argumentValues.bookFilePath,
        argumentValues.newBookKeyFilePath,
        argumentValues.backupFilePath,
        argumentValues.backupBookKeyFilePath);
    return new RestoreBook(
        argumentValues.bookFilePath,
        argumentValues.newBookKeyFilePath,
        argumentValues.backupFilePath,
        argumentValues.backupBookKeyFilePath,
        argumentValues.replaceExistingBook,
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static void applyRestoreBookArgument(
      RestoreBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolBookAccessOptions.BOOK_FILE ->
          argumentValues.bookFilePath =
              requireSingleRestorePath(
                  argumentValues.bookFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.BOOK_FILE);
      case ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE ->
          argumentValues.newBookKeyFilePath =
              requireSingleRestorePath(
                  argumentValues.newBookKeyFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
      case ProtocolBookAccessOptions.BACKUP_FILE ->
          argumentValues.backupFilePath =
              requireSingleRestorePath(
                  argumentValues.backupFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.BACKUP_FILE);
      case ProtocolBookAccessOptions.BACKUP_KEY_FILE ->
          argumentValues.backupBookKeyFilePath =
              requireSingleRestorePath(
                  argumentValues.backupBookKeyFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.BACKUP_KEY_FILE);
      case ProtocolBookAccessOptions.REPLACE_EXISTING_BOOK -> {
        if (argumentValues.replaceExistingBook) {
          throw CliArgumentValueParser.invalid(
              ProtocolBookAccessOptions.REPLACE_EXISTING_BOOK,
              "Duplicate argument: " + ProtocolBookAccessOptions.REPLACE_EXISTING_BOOK);
        }
        argumentValues.replaceExistingBook = true;
      }
      case ProtocolOptions.Presentation.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.Presentation.OUTPUT),
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
    private @Nullable Path newBookKeyFilePath;
    private @Nullable Path backupFilePath;
    private @Nullable Path backupBookKeyFilePath;
    private @Nullable OutputMode outputMode;
    private boolean replaceExistingBook;
  }
}
