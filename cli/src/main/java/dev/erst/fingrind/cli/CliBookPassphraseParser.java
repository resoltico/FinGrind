package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical passphrase-source parsing and vocabulary mapping for book-addressing commands. */
final class CliBookPassphraseParser {
  private CliBookPassphraseParser() {}

  static PassphraseSourceKind requireSinglePassphraseSource(
      @Nullable PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw CliArgumentValueParser.invalid(
          candidateSource.optionName(),
          "Exactly one book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  static BookAccess.PassphraseSource passphraseSource(
      @Nullable PassphraseSourceKind passphraseSourceKind, @Nullable Path bookKeyFilePath) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE ->
          new BookAccess.PassphraseSource.KeyFile(
              Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath"));
      case STANDARD_INPUT -> BookAccess.PassphraseSource.StandardInput.INSTANCE;
      case INTERACTIVE_PROMPT -> BookAccess.PassphraseSource.InteractivePrompt.INSTANCE;
    };
  }

  /** Canonical passphrase-source selections accepted by the CLI parser. */
  enum PassphraseSourceKind {
    KEY_FILE(ProtocolBookAccessOptions.BOOK_KEY_FILE),
    STANDARD_INPUT(ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN),
    INTERACTIVE_PROMPT(ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT);

    private final String optionName;

    PassphraseSourceKind(String optionName) {
      this.optionName = optionName;
    }

    String optionName() {
      return optionName;
    }
  }
}
