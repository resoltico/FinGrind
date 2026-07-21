package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `rekey-book`. */
final class CliRekeyBookArguments {
  private static final List<String> REKEY_BOOK_OPTIONS =
      List.of(
          ProtocolBookAccessOptions.BOOK_FILE,
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
          ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT,
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          ProtocolOptions.Presentation.OUTPUT);

  private CliRekeyBookArguments() {}

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    RekeyBookArgumentValues argumentValues = new RekeyBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyRekeyBookArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    if (argumentValues.bookAccess.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_FILE,
          "A " + ProtocolBookAccessOptions.BOOK_FILE + " argument is required.");
    }
    if (argumentValues.bookAccess.passphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          "Exactly one current book passphrase source is required: "
              + ProtocolBookAccessOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (argumentValues.newBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
          "A " + ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " argument is required.");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            argumentValues.bookAccess.passphraseSourceKind,
            argumentValues.bookAccess.bookKeyFilePath);
    CliBookPathValidator.validateDistinctRekeyTarget(
        argumentValues.bookAccess.bookFilePath,
        currentPassphraseSource,
        argumentValues.newBookKeyFilePath);
    CliBookPathValidator.validateStandardInputUsage(currentPassphraseSource, null);
    return new RekeyBook(
        new BookAccess(
            argumentValues.bookAccess.bookFilePath, currentPassphraseSource, java.util.List.of()),
        argumentValues.newBookKeyFilePath,
        CliOptionModes.resolvedOutputMode(argumentValues.bookAccess.outputMode));
  }

  private static void applyRekeyBookArgument(
      RekeyBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    if (CliMaintenanceBookAccessArguments.apply(
        argumentValues.bookAccess, argument, argumentIterator)) {
      return;
    }
    if (ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE.equals(argument)) {
      argumentValues.newBookKeyFilePath =
          CliMaintenanceBookAccessArguments.requireSinglePath(
              argumentValues.newBookKeyFilePath,
              argumentIterator,
              ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
      return;
    }
    throw CliArgumentValueParser.unsupportedArgument(argument, REKEY_BOOK_OPTIONS);
  }

  /** Mutable parse accumulator for rekey-book command options before validation. */
  private static final class RekeyBookArgumentValues {
    private final CliMaintenanceBookAccessArguments.Values bookAccess =
        new CliMaintenanceBookAccessArguments.Values();
    private @Nullable Path newBookKeyFilePath;
  }
}
