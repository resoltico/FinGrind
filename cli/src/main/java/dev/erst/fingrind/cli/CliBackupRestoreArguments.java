package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Parses the attested backup and restore artifact protocols. */
final class CliBackupRestoreArguments {
  private static final List<String> RESTORE_BOOK_OPTIONS =
      List.of(
          ProtocolBookAccessOptions.BOOK_FILE,
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          ProtocolBookAccessOptions.BACKUP_FILE,
          ProtocolBookAccessOptions.BACKUP_KEY_FILE,
          ProtocolOptions.Attestation.PRINCIPAL_ID,
          ProtocolOptions.Attestation.KEY_FILE,
          ProtocolOptions.Attestation.PASSPHRASE_FILE,
          ProtocolOptions.Presentation.OUTPUT);

  private CliBackupRestoreArguments() {}

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    return CliBackupBookArguments.parseBackupBookCommand(arguments);
  }

  static CliCommand parseRestoreBookCommand(List<String> arguments) {
    RestoreBookArgumentValues values = new RestoreBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyRestoreBookArgument(values, argumentIterator.next(), argumentIterator);
    }
    if (values.bookFilePath == null) {
      throw required(ProtocolBookAccessOptions.BOOK_FILE);
    }
    if (values.newBookKeyFilePath == null) {
      throw required(ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
    }
    if (values.backupFilePath == null) {
      throw required(ProtocolBookAccessOptions.BACKUP_FILE);
    }
    if (values.backupBookKeyFilePath == null) {
      throw required(ProtocolBookAccessOptions.BACKUP_KEY_FILE);
    }
    CliBookPathValidator.validateDistinctRestorePaths(
        values.bookFilePath,
        values.newBookKeyFilePath,
        values.backupFilePath,
        values.backupBookKeyFilePath);
    return new RestoreBook(
        values.bookFilePath,
        values.newBookKeyFilePath,
        values.backupFilePath,
        values.backupBookKeyFilePath,
        resolveRequiredAttestationCredentialSources(values),
        CliOptionModes.resolvedOutputMode(values.outputMode));
  }

  private static void applyRestoreBookArgument(
      RestoreBookArgumentValues values, String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolBookAccessOptions.BOOK_FILE ->
          values.bookFilePath =
              requireSingleRestorePath(
                  values.bookFilePath, argumentIterator, ProtocolBookAccessOptions.BOOK_FILE);
      case ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE ->
          values.newBookKeyFilePath =
              requireSingleRestorePath(
                  values.newBookKeyFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
      case ProtocolBookAccessOptions.BACKUP_FILE ->
          values.backupFilePath =
              requireSingleRestorePath(
                  values.backupFilePath, argumentIterator, ProtocolBookAccessOptions.BACKUP_FILE);
      case ProtocolBookAccessOptions.BACKUP_KEY_FILE ->
          values.backupBookKeyFilePath =
              requireSingleRestorePath(
                  values.backupBookKeyFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.BACKUP_KEY_FILE);
      case ProtocolOptions.Attestation.PRINCIPAL_ID ->
          values.principalIds.add(
              CliArgumentValueParser.requireValidArgument(
                  ProtocolOptions.Attestation.PRINCIPAL_ID,
                  () ->
                      UUID.fromString(
                          CliOptionValues.requireValue(
                              argumentIterator, ProtocolOptions.Attestation.PRINCIPAL_ID))));
      case ProtocolOptions.Attestation.KEY_FILE ->
          values.keyFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.KEY_FILE));
      case ProtocolOptions.Attestation.PASSPHRASE_FILE ->
          values.passphraseFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.PASSPHRASE_FILE));
      case ProtocolOptions.Presentation.OUTPUT ->
          values.outputMode =
              CliOptionModes.requireOutputMode(
                  values.outputMode,
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default -> throw CliArgumentValueParser.unsupportedArgument(argument, RESTORE_BOOK_OPTIONS);
    }
  }

  private static List<AttestationCredentialSource> resolveRequiredAttestationCredentialSources(
      RestoreBookArgumentValues values) {
    int count = values.principalIds.size();
    if (count == 0
        || count > AttestationAuthorizationLimits.MAXIMUM_QUORUM
        || values.keyFiles.size() != count
        || values.passphraseFiles.size() != count) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.PRINCIPAL_ID,
          "Provide one through "
              + AttestationAuthorizationLimits.MAXIMUM_QUORUM
              + " aligned attestation credential triples: "
              + ProtocolOptions.Attestation.PRINCIPAL_ID
              + ", "
              + ProtocolOptions.Attestation.KEY_FILE
              + ", and "
              + ProtocolOptions.Attestation.PASSPHRASE_FILE
              + ".");
    }
    try {
      List<AttestationCredentialSource> sources = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        sources.add(
            new AttestationCredentialSource(
                values.principalIds.get(index),
                values.keyFiles.get(index),
                values.passphraseFiles.get(index)));
      }
      return List.copyOf(sources);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.PRINCIPAL_ID,
          java.util.Objects.requireNonNull(
              exception.getMessage(),
              "AttestationCredentialSource must report why a credential triple is invalid."),
          exception);
    }
  }

  private static Path requireSingleRestorePath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  private static IllegalArgumentException required(String option) {
    return CliArgumentValueParser.invalid(option, "A " + option + " argument is required.");
  }

  /** Mutable restore command parse state. */
  private static final class RestoreBookArgumentValues {
    private final List<UUID> principalIds = new ArrayList<>();
    private final List<Path> keyFiles = new ArrayList<>();
    private final List<Path> passphraseFiles = new ArrayList<>();
    private @Nullable Path bookFilePath;
    private @Nullable Path newBookKeyFilePath;
    private @Nullable Path backupFilePath;
    private @Nullable Path backupBookKeyFilePath;
    private @Nullable OutputMode outputMode;
  }
}
