package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical passphrase-source parsing and vocabulary mapping for book-addressing commands. */
final class CliBookPassphraseArgumentSupport {
  private CliBookPassphraseArgumentSupport() {}

  static PassphraseSourceKind requireSinglePassphraseSource(
      @Nullable PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw CliArgumentSupport.invalid(
          candidateSource.optionName(),
          "Exactly one book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  static PassphraseSourceKind requireSingleReplacementPassphraseSource(
      @Nullable PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw CliArgumentSupport.invalid(
          replacementOptionName(candidateSource),
          "Exactly one replacement book passphrase source is permitted per command.");
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

  static String replacementOptionName(PassphraseSourceKind passphraseSourceKind) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE -> ProtocolOptions.NEW_BOOK_KEY_FILE;
      case STANDARD_INPUT -> ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN;
      case INTERACTIVE_PROMPT -> ProtocolOptions.NEW_BOOK_PASSPHRASE_PROMPT;
    };
  }

  /** Canonical passphrase-source selections accepted by the CLI parser. */
  enum PassphraseSourceKind {
    KEY_FILE(ProtocolOptions.BOOK_KEY_FILE),
    STANDARD_INPUT(ProtocolOptions.BOOK_PASSPHRASE_STDIN),
    INTERACTIVE_PROMPT(ProtocolOptions.BOOK_PASSPHRASE_PROMPT);

    private final String optionName;

    PassphraseSourceKind(String optionName) {
      this.optionName = optionName;
    }

    String optionName() {
      return optionName;
    }
  }
}
