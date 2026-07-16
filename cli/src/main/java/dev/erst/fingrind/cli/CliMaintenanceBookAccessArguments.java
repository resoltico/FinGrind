package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Owns the shared existing-book access grammar used by protected-book maintenance commands. */
final class CliMaintenanceBookAccessArguments {
  private CliMaintenanceBookAccessArguments() {}

  static boolean apply(Values values, String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolBookAccessOptions.BOOK_FILE ->
          values.bookFilePath = requireSinglePath(values.bookFilePath, argumentIterator, argument);
      case ProtocolBookAccessOptions.BOOK_KEY_FILE -> {
        values.passphraseSourceKind =
            CliBookPassphraseParser.requireSinglePassphraseSource(
                values.passphraseSourceKind, CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
        values.bookKeyFilePath = CliOptionValues.requirePathOptionValue(argumentIterator, argument);
      }
      case ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN ->
          values.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  values.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
      case ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT ->
          values.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  values.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
      case ProtocolOptions.Presentation.OUTPUT ->
          values.outputMode =
              CliOptionModes.requireOutputMode(
                  values.outputMode,
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default -> {
        return false;
      }
    }
    return true;
  }

  static Path requireSinglePath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  /** Mutable common state for one maintenance command's existing-book access flags. */
  static final class Values {
    @Nullable Path bookFilePath;
    @Nullable Path bookKeyFilePath;
    CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind;
    @Nullable OutputMode outputMode;
  }
}
