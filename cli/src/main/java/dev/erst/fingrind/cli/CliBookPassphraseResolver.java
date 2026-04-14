package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Resolves one CLI-visible passphrase source into one zeroizable UTF-8 passphrase payload. */
final class CliBookPassphraseResolver {
  private static final String NO_INTERACTIVE_CONSOLE_MESSAGE =
      "FinGrind cannot prompt for a book passphrase because no interactive console is available.";

  private final InputStream inputStream;
  private final Terminal terminal;

  CliBookPassphraseResolver(InputStream inputStream, Terminal terminal) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
    this.terminal = Objects.requireNonNull(terminal, "terminal");
  }

  /** Resolves the selected book passphrase source for one CLI command invocation. */
  SqliteBookPassphrase resolve(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile ->
          SqliteBookKeyFile.load(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> readFromStandardInput();
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          readFromInteractivePrompt(bookAccess.bookFilePath());
    };
  }

  private SqliteBookPassphrase readFromStandardInput() {
    try {
      return SqliteBookPassphrase.fromUtf8Bytes("standard input", inputStream.readAllBytes());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the FinGrind book passphrase from standard input.", exception);
    }
  }

  private SqliteBookPassphrase readFromInteractivePrompt(Path bookFilePath) {
    Path normalizedPath = bookFilePath.toAbsolutePath().normalize();
    char[] password =
        terminal.readPassword("FinGrind book passphrase for %s: ".formatted(normalizedPath));
    if (password == null) {
      throw new IllegalStateException(
          "FinGrind did not receive a book passphrase from the interactive console.");
    }
    return SqliteBookPassphrase.fromCharacters(
        "interactive prompt for " + normalizedPath, password);
  }

  /** Reads one passphrase from an interactive terminal without echo. */
  @FunctionalInterface
  interface Terminal {
    /** Prompts for one passphrase and returns the entered characters. */
    char[] readPassword(String prompt);
  }

  static Terminal systemTerminal() {
    return new ConsoleBackedTerminal(CliBookPassphraseResolver::systemConsoleReader);
  }

  static Optional<Terminal> systemConsoleReader() {
    return Optional.ofNullable(System.console())
        .map(console -> promptText -> console.readPassword("%s", promptText));
  }

  /** Terminal adapter that obtains the controlling prompt bridge lazily for each read. */
  static final class ConsoleBackedTerminal implements Terminal {
    private final Supplier<Optional<Terminal>> readerSupplier;

    ConsoleBackedTerminal(Supplier<Optional<Terminal>> readerSupplier) {
      this.readerSupplier = Objects.requireNonNull(readerSupplier, "readerSupplier");
    }

    @Override
    public char[] readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      Optional<Terminal> reader = Objects.requireNonNull(readerSupplier.get(), "reader");
      if (reader.isEmpty()) {
        throw noConsole();
      }
      return reader.orElseThrow().readPassword(prompt);
    }
  }

  private static IllegalStateException noConsole() {
    return new IllegalStateException(NO_INTERACTIVE_CONSOLE_MESSAGE);
  }
}
