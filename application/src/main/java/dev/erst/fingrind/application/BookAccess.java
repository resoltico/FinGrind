package dev.erst.fingrind.application;

import java.nio.file.Path;
import java.util.Objects;

/** One durable book file plus one supported passphrase-source selection. */
public record BookAccess(Path bookFilePath, PassphraseSource passphraseSource) {
  public BookAccess {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
  }

  /** Supported CLI-visible passphrase transport selections for one protected book command. */
  public sealed interface PassphraseSource
      permits PassphraseSource.KeyFile,
          PassphraseSource.StandardInput,
          PassphraseSource.InteractivePrompt {
    /** Returns the canonical CLI option name for this passphrase source. */
    String optionName();

    /** Passphrase source that reads one UTF-8 passphrase file from the filesystem. */
    record KeyFile(Path bookKeyFilePath) implements PassphraseSource {
      public KeyFile {
        Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
      }

      @Override
      public String optionName() {
        return "--book-key-file";
      }
    }

    /** Passphrase source that reads one UTF-8 passphrase payload from standard input. */
    record StandardInput() implements PassphraseSource {
      public static final StandardInput INSTANCE = new StandardInput();

      @Override
      public String optionName() {
        return "--book-passphrase-stdin";
      }
    }

    /** Passphrase source that reads one passphrase from the controlling terminal without echo. */
    record InteractivePrompt() implements PassphraseSource {
      public static final InteractivePrompt INSTANCE = new InteractivePrompt();

      @Override
      public String optionName() {
        return "--book-passphrase-prompt";
      }
    }
  }
}
