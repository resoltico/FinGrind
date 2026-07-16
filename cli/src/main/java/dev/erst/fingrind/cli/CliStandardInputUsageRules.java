package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
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
        && ProtocolOptions.Request.STDIN_TOKEN.equals(requestFile.toString())
        && isStandardInput(passphraseSource)) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the book passphrase and the request JSON.");
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
