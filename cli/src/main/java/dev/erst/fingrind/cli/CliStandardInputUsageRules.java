package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Standard-input exclusivity rules for passphrase and request payload channels. */
final class CliStandardInputUsageRules {
  private CliStandardInputUsageRules() {}

  static void validateStandardInputUsage(
      BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    if (requestFile != null
        && ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())
        && isStandardInput(passphraseSource)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the book passphrase and the request JSON.");
    }
  }

  static void validateRekeyStandardInputUsage(
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (isStandardInput(currentPassphraseSource) && isStandardInput(replacementPassphraseSource)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the current and new book passphrases.");
    }
  }

  private static boolean isStandardInput(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> false;
      case BookAccess.PassphraseSource.StandardInput _ -> true;
      case BookAccess.PassphraseSource.InteractivePrompt _ -> false;
    };
  }
}
