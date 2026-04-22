package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

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
  ContractDecision<SqliteBookPassphrase> resolve(BookAccess bookAccess) {
    return resolve(bookAccess, PromptStyle.SINGLE);
  }

  /** Resolves the selected book passphrase source for one CLI command invocation. */
  ContractDecision<SqliteBookPassphrase> resolve(BookAccess bookAccess, PromptStyle promptStyle) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return resolve(bookAccess.bookFilePath(), bookAccess.passphraseSource(), promptStyle);
  }

  /** Resolves one explicit passphrase source for the selected book path. */
  ContractDecision<SqliteBookPassphrase> resolve(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, PromptStyle promptStyle) {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
    Objects.requireNonNull(promptStyle, "promptStyle");
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile ->
          SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> readFromStandardInput();
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          readFromInteractivePrompt(bookFilePath, promptStyle);
    };
  }

  private ContractDecision<SqliteBookPassphrase> readFromStandardInput() {
    try {
      return SqliteBookPassphrase.fromUtf8BytesDecision(
          "standard input", inputStream.readAllBytes());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to read the FinGrind book passphrase from standard input.", exception);
    }
  }

  private ContractDecision<SqliteBookPassphrase> readFromInteractivePrompt(
      Path bookFilePath, PromptStyle promptStyle) {
    Path normalizedPath = bookFilePath.toAbsolutePath().normalize();
    ContractDecision<char[]> passwordDecision =
        terminal.readPassword(promptStyle.primaryPrompt(normalizedPath));
    char[] password;
    switch (passwordDecision) {
      case ContractDecision.Accepted<char[]>(char[] acceptedPassword) ->
          password = acceptedPassword;
      case ContractDecision.Rejected<char[]>(ContractFailure failure) -> {
        return rejectedPassphrase(failure);
      }
    }
    if (promptStyle == PromptStyle.SINGLE) {
      return SqliteBookPassphrase.fromCharactersDecision(
          "interactive prompt for " + normalizedPath, password);
    }
    ContractDecision<char[]> confirmationDecision =
        terminal.readPassword(promptStyle.confirmationPrompt(normalizedPath));
    char[] confirmation;
    switch (confirmationDecision) {
      case ContractDecision.Accepted<char[]>(char[] acceptedConfirmation) ->
          confirmation = acceptedConfirmation;
      case ContractDecision.Rejected<char[]>(ContractFailure failure) -> {
        Arrays.fill(password, '\0');
        return rejectedPassphrase(failure);
      }
    }
    if (!Arrays.equals(password, confirmation)) {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmation, '\0');
      return ContractDecision.rejected(
          interactivePromptFailure(
              "FinGrind did not receive matching book passphrases from the interactive console."));
    }
    Arrays.fill(confirmation, '\0');
    return SqliteBookPassphrase.fromCharactersDecision(
        "interactive prompt for " + normalizedPath, password);
  }

  /** Reads one passphrase from an interactive terminal without echo. */
  @FunctionalInterface
  interface Terminal {
    /** Prompts for one passphrase and returns the entered characters. */
    ContractDecision<char[]> readPassword(String prompt);
  }

  static Terminal systemTerminal() {
    return new ConsoleBackedTerminal(CliBookPassphraseResolver::systemConsoleReader);
  }

  static Optional<Terminal> systemConsoleReader() {
    return systemConsoleReader(System.console());
  }

  static Optional<Terminal> systemConsoleReader(@Nullable Console consoleHandle) {
    return Optional.ofNullable(consoleHandle)
        .map(console -> new PromptingConsoleTerminal(console::readPassword));
  }

  /** Terminal adapter that obtains the controlling prompt bridge lazily for each read. */
  static final class ConsoleBackedTerminal implements Terminal {
    private final Supplier<Optional<Terminal>> readerSupplier;

    ConsoleBackedTerminal(Supplier<Optional<Terminal>> readerSupplier) {
      this.readerSupplier = Objects.requireNonNull(readerSupplier, "readerSupplier");
    }

    @Override
    public ContractDecision<char[]> readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      Optional<Terminal> reader = Objects.requireNonNull(readerSupplier.get(), "reader");
      if (reader.isEmpty()) {
        return ContractDecision.rejected(noConsole());
      }
      return reader.orElseThrow().readPassword(prompt);
    }
  }

  /** Typed console seam for password prompts used by the interactive CLI flow. */
  @FunctionalInterface
  interface PromptingConsole {
    /** Reads one password from the underlying console prompt and may return {@code null} on EOF. */
    char @Nullable [] readPassword(String format, Object... arguments);
  }

  /** Shared terminal adapter that converts one typed prompt seam into FinGrind decisions. */
  static class PromptingConsoleTerminal implements Terminal {
    private final PromptingConsole promptingConsole;

    PromptingConsoleTerminal(PromptingConsole promptingConsole) {
      this.promptingConsole = Objects.requireNonNull(promptingConsole, "promptingConsole");
    }

    @Override
    public ContractDecision<char[]> readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      try {
        char @Nullable [] password = promptingConsole.readPassword("%s", prompt);
        if (password == null) {
          return ContractDecision.rejected(
              interactivePromptFailure(
                  "FinGrind did not receive a book passphrase from the interactive console."));
        }
        return ContractDecision.accepted(password);
      } catch (RuntimeException exception) {
        return ContractDecision.rejected(
            ContractErrors.Descriptor.INTERACTIVE_PROMPT_FAILED.failure(
                "Failed to prompt for a book passphrase from the interactive console.",
                "Rerun the command from a supported interactive terminal, or use --book-key-file or --book-passphrase-stdin instead.",
                null));
      }
    }
  }

  private static ContractFailure noConsole() {
    return ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.failure(
        NO_INTERACTIVE_CONSOLE_MESSAGE,
        "Rerun the command from an interactive terminal, or use --book-key-file or --book-passphrase-stdin instead.",
        null);
  }

  private static ContractFailure interactivePromptFailure(String message) {
    return ContractErrors.Descriptor.INTERACTIVE_PROMPT_FAILED.failure(
        message,
        "Rerun the command from a supported interactive terminal and provide one valid passphrase, or use --book-key-file or --book-passphrase-stdin instead.",
        null);
  }

  private static ContractDecision<SqliteBookPassphrase> rejectedPassphrase(
      ContractFailure failure) {
    return ContractDecision.rejected(failure);
  }

  /** Prompt modes for existing-book secrets versus newly entered replacement secrets. */
  enum PromptStyle {
    SINGLE,
    CONFIRMED_NEW_SECRET;

    String primaryPrompt(Path normalizedPath) {
      return switch (this) {
        case SINGLE -> "FinGrind book passphrase for %s: ".formatted(normalizedPath);
        case CONFIRMED_NEW_SECRET ->
            "New FinGrind book passphrase for %s: ".formatted(normalizedPath);
      };
    }

    String confirmationPrompt(Path normalizedPath) {
      if (this != CONFIRMED_NEW_SECRET) {
        throw new IllegalStateException("This prompt style does not support confirmation.");
      }
      return "Confirm new FinGrind book passphrase for %s: ".formatted(normalizedPath);
    }
  }
}
