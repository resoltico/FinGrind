package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Parses backup-specific arguments after the shared protected-book grammar succeeds. */
final class CliBackupBookArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec BACKUP_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolBookAccessOptions.BACKUP_FILE,
              ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
              ProtocolBookAccessOptions.BACKUP_ID,
              ProtocolOptions.Presentation.OUTPUT),
          List.of());

  private CliBackupBookArguments() {}

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, BACKUP_BOOK_ARGUMENTS);
    BackupBookArgumentValues values = parseArgumentValues(parsedArguments.commandArguments());
    Path backupFilePath = values.requireBackupFilePath();
    Path backupBookKeyFilePath = values.requireBackupBookKeyFilePath();
    CliBookPathValidator.validateDistinctBackupPaths(
        parsedArguments.bookAccess().bookFilePath(),
        parsedArguments.bookAccess().passphraseSource(),
        backupFilePath,
        backupBookKeyFilePath);
    return new BackupBook(
        parsedArguments.bookAccess(),
        backupFilePath,
        backupBookKeyFilePath,
        values.requireBackupId(),
        CliOptionModes.resolvedOutputMode(values.outputMode));
  }

  private static BackupBookArgumentValues parseArgumentValues(List<String> commandArguments) {
    BackupBookArgumentValues values = new BackupBookArgumentValues();
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      values.apply(argumentIterator.next(), argumentIterator);
    }
    return values;
  }

  /** Accumulates one backup command tail before its required fields are resolved. */
  private static final class BackupBookArgumentValues {
    private @Nullable Path backupFilePath;
    private @Nullable Path backupBookKeyFilePath;
    private @Nullable UUID backupId;
    private @Nullable OutputMode outputMode;

    private void apply(String argument, ListIterator<String> argumentIterator) {
      switch (argument) {
        case ProtocolBookAccessOptions.BACKUP_FILE ->
            backupFilePath =
                requireSinglePath(
                    backupFilePath, argumentIterator, ProtocolBookAccessOptions.BACKUP_FILE);
        case ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE ->
            backupBookKeyFilePath =
                requireSinglePath(
                    backupBookKeyFilePath,
                    argumentIterator,
                    ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
        case ProtocolBookAccessOptions.BACKUP_ID ->
            backupId = requireBackupId(argumentIterator, backupId);
        case ProtocolOptions.Presentation.OUTPUT ->
            outputMode =
                CliOptionModes.requireOutputMode(
                    outputMode,
                    CliOptionValues.requireValue(
                        argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                    CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
        default -> throw CliArgumentValueParser.unsupportedArgument(argument, List.of());
      }
    }

    private static Path requireSinglePath(
        @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
      if (currentPath != null) {
        throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
      }
      return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
    }

    private static UUID requireBackupId(
        ListIterator<String> argumentIterator, @Nullable UUID currentBackupId) {
      if (currentBackupId != null) {
        throw CliArgumentValueParser.invalid(
            ProtocolBookAccessOptions.BACKUP_ID,
            "Duplicate argument: " + ProtocolBookAccessOptions.BACKUP_ID);
      }
      return CliArgumentValueParser.requireValidArgument(
          ProtocolBookAccessOptions.BACKUP_ID,
          () ->
              UUID.fromString(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolBookAccessOptions.BACKUP_ID)));
    }

    private Path requireBackupFilePath() {
      return require(backupFilePath, ProtocolBookAccessOptions.BACKUP_FILE);
    }

    private Path requireBackupBookKeyFilePath() {
      return require(backupBookKeyFilePath, ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
    }

    private UUID requireBackupId() {
      return require(backupId, ProtocolBookAccessOptions.BACKUP_ID);
    }

    private static <T> T require(@Nullable T value, String optionName) {
      if (value == null) {
        throw CliArgumentValueParser.invalid(
            optionName, "A " + optionName + " argument is required.");
      }
      return value;
    }
  }
}
