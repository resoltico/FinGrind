package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePassphraseResolver;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one CLI-visible passphrase source into one UTF-8 passphrase payload with owned-buffer
 * overwrite.
 */
final class CliBookPassphraseResolver implements SqlitePassphraseResolver {
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
  @Override
  public ContractDecision<SqliteBookPassphrase> resolve(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      SqlitePassphraseIntent intent) {
    return resolve(bookFilePath, passphraseSource, promptStyle(intent));
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
      return readStandardInputBytes()
          .fold(
              bytes -> SqliteBookPassphrase.fromUtf8BytesDecision("standard input", bytes),
              ContractDecision::rejected);
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
              "Failed to read the FinGrind book passphrase from standard input.",
              "Inspect the standard-input passphrase source, confirm the pipeline is readable, and rerun the command.",
              null));
    }
  }

  private ContractDecision<byte[]> readStandardInputBytes() throws IOException {
    byte[] buffer = inputStream.readNBytes(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES + 1);
    if (buffer.length <= SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES) {
      return ContractDecision.accepted(buffer);
    }
    return ContractDecision.rejected(
        oversizedPassphraseSource(
            "standard input",
            "FinGrind book passphrase input from standard input exceeded the %d-byte limit."
                .formatted(SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES)));
  }

  private ContractDecision<SqliteBookPassphrase> readFromInteractivePrompt(
      Path bookFilePath, PromptStyle promptStyle) {
    Path normalizedPath = bookFilePath.toAbsolutePath().normalize();
    String displayPath = CliHumanDisplay.path(normalizedPath);
    ContractDecision<char[]> passwordDecision =
        terminal.readPassword(promptStyle.primaryPrompt(displayPath));
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
        terminal.readPassword(promptStyle.confirmationPrompt(displayPath));
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
    return new PromptingConsoleLookupTerminal(CliBookPassphraseResolver::systemPromptingConsole);
  }

  private static @Nullable PromptingConsole systemPromptingConsole() {
    java.io.Console console = availableSystemConsole();
    return Optional.ofNullable(console)
        .map(c -> availableSystemPromptingConsole(wrap(c::isTerminal, c::readPassword)))
        .orElse(null);
  }

  private static java.io.@Nullable Console availableSystemConsole() {
    return System.console();
  }

  static @Nullable PromptingConsole availableSystemPromptingConsole(
      @Nullable SystemPromptingConsole systemConsole) {
    if (systemConsole == null || !systemConsole.isTerminal()) {
      return null;
    }
    return systemConsole;
  }

  /** Terminal adapter that obtains the controlling prompt bridge lazily for each read. */
  static final class PromptingConsoleLookupTerminal implements Terminal {
    private final Supplier<@Nullable PromptingConsole> promptingConsoleSupplier;

    PromptingConsoleLookupTerminal(Supplier<@Nullable PromptingConsole> promptingConsoleSupplier) {
      this.promptingConsoleSupplier =
          Objects.requireNonNull(promptingConsoleSupplier, "promptingConsoleSupplier");
    }

    @Override
    public ContractDecision<char[]> readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      PromptingConsole promptingConsole = promptingConsoleSupplier.get();
      if (promptingConsole == null) {
        return ContractDecision.rejected(noConsole());
      }
      return new PromptingConsoleTerminal(promptingConsole).readPassword(prompt);
    }
  }

  /** Typed console seam for password prompts used by the interactive CLI flow. */
  @FunctionalInterface
  interface PromptingConsole {
    /** Reads one password from the underlying console prompt and may return {@code null} on EOF. */
    char @Nullable [] readPassword(String prompt);
  }

  /** Typed system-console seam that exposes prompt and terminal state together. */
  interface SystemPromptingConsole extends PromptingConsole {
    /** Reports whether the backing console is interactive for password prompting. */
    boolean isTerminal();
  }

  /** Typed boolean seam for one console-terminal check. */
  @FunctionalInterface
  interface TerminalState {
    /** Reports whether the wrapped console is interactive for prompting. */
    boolean isTerminal();
  }

  /** Typed formatted-password seam matching the JDK console contract. */
  @FunctionalInterface
  interface FormattedPasswordReader {
    /**
     * Reads a password with one format string plus arguments and may return {@code null} on EOF.
     */
    char @Nullable [] readPassword(String format, Object... arguments);
  }

  static SystemPromptingConsole wrap(
      TerminalState terminalState, FormattedPasswordReader passwordReader) {
    return new WrappedConsole(terminalState, passwordReader);
  }

  /** JDK console adapter that exposes one interactive-terminal check plus prompt reads. */
  static final class WrappedConsole implements SystemPromptingConsole {
    private final TerminalState terminalState;
    private final FormattedPasswordReader passwordReader;

    WrappedConsole(TerminalState terminalState, FormattedPasswordReader passwordReader) {
      this.terminalState = Objects.requireNonNull(terminalState, "terminalState");
      this.passwordReader = Objects.requireNonNull(passwordReader, "passwordReader");
    }

    @Override
    public boolean isTerminal() {
      return terminalState.isTerminal();
    }

    @Override
    public char @Nullable [] readPassword(String prompt) {
      return passwordReader.readPassword("%s", prompt);
    }
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
        char @Nullable [] password = promptingConsole.readPassword(prompt);
        if (password == null) {
          return ContractDecision.rejected(
              interactivePromptFailure(
                  "FinGrind did not receive a book passphrase from the interactive console."));
        }
        return ContractDecision.accepted(password);
      } catch (RuntimeException | IOError exception) {
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

  private static ContractFailure oversizedPassphraseSource(
      String sourceDescription, String message) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        message,
        "Provide one non-empty single-line UTF-8 passphrase through "
            + sourceDescription
            + " within the "
            + SqliteBookPassphrase.MAX_UTF8_SOURCE_BYTES
            + "-byte limit, then rerun the command.",
        null);
  }

  private static ContractDecision<SqliteBookPassphrase> rejectedPassphrase(
      ContractFailure failure) {
    return ContractDecision.rejected(failure);
  }

  private static PromptStyle promptStyle(SqlitePassphraseIntent intent) {
    return switch (Objects.requireNonNull(intent, "intent")) {
      case EXISTING_SECRET -> PromptStyle.SINGLE;
      case NEW_SECRET -> PromptStyle.CONFIRMED_NEW_SECRET;
    };
  }

  /** Prompt modes for existing-book secrets versus newly entered replacement secrets. */
  enum PromptStyle {
    SINGLE,
    CONFIRMED_NEW_SECRET;

    String primaryPrompt(String displayPath) {
      return switch (this) {
        case SINGLE -> "FinGrind book passphrase for %s: ".formatted(displayPath);
        case CONFIRMED_NEW_SECRET -> "New FinGrind book passphrase for %s: ".formatted(displayPath);
      };
    }

    String confirmationPrompt(String displayPath) {
      if (this != CONFIRMED_NEW_SECRET) {
        throw new IllegalStateException("This prompt style does not support confirmation.");
      }
      return "Confirm new FinGrind book passphrase for %s: ".formatted(displayPath);
    }
  }
}
