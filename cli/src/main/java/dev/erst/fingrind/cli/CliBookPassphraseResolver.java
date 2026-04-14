package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAccess;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
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
    return resolve(bookAccess, PromptStyle.SINGLE);
  }

  /** Resolves the selected book passphrase source for one CLI command invocation. */
  SqliteBookPassphrase resolve(BookAccess bookAccess, PromptStyle promptStyle) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return resolve(bookAccess.bookFilePath(), bookAccess.passphraseSource(), promptStyle);
  }

  /** Resolves one explicit passphrase source for the selected book path. */
  SqliteBookPassphrase resolve(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, PromptStyle promptStyle) {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(passphraseSource, "passphraseSource");
    Objects.requireNonNull(promptStyle, "promptStyle");
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile ->
          SqliteBookKeyFile.load(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> readFromStandardInput();
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          readFromInteractivePrompt(bookFilePath, promptStyle);
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

  private SqliteBookPassphrase readFromInteractivePrompt(
      Path bookFilePath, PromptStyle promptStyle) {
    Path normalizedPath = bookFilePath.toAbsolutePath().normalize();
    char[] password = terminal.readPassword(promptStyle.primaryPrompt(normalizedPath));
    if (password == null) {
      throw new IllegalStateException(
          "FinGrind did not receive a book passphrase from the interactive console.");
    }
    if (promptStyle == PromptStyle.SINGLE) {
      return SqliteBookPassphrase.fromCharacters(
          "interactive prompt for " + normalizedPath, password);
    }
    char[] confirmation = terminal.readPassword(promptStyle.confirmationPrompt(normalizedPath));
    if (confirmation == null) {
      Arrays.fill(password, '\0');
      throw new IllegalStateException(
          "FinGrind did not receive a confirmed book passphrase from the interactive console.");
    }
    if (!Arrays.equals(password, confirmation)) {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmation, '\0');
      throw new IllegalStateException(
          "FinGrind did not receive matching book passphrases from the interactive console.");
    }
    Arrays.fill(confirmation, '\0');
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
    return systemConsoleReader(System.console());
  }

  static Optional<Terminal> systemConsoleReader(Object consoleHandle) {
    return Optional.ofNullable(consoleHandle).map(ReflectiveConsoleTerminal::new);
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

  /** Adapts a JDK console-like object by invoking its readPassword(format, args...) method. */
  static final class ReflectiveConsoleTerminal implements Terminal {
    private final Object consoleHandle;
    private final Method readPasswordMethod;

    ReflectiveConsoleTerminal(Object consoleHandle) {
      this.consoleHandle = Objects.requireNonNull(consoleHandle, "consoleHandle");
      this.readPasswordMethod = resolveReadPasswordMethod(consoleHandle);
    }

    @Override
    public char[] readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      try {
        return (char[]) readPasswordMethod.invoke(consoleHandle, "%s", new Object[] {prompt});
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException(
            "Failed to prompt for a book passphrase from the interactive console.", exception);
      }
    }

    private static Method resolveReadPasswordMethod(Object consoleHandle) {
      try {
        Method readPasswordMethod =
            consoleHandle
                .getClass()
                .getDeclaredMethod("readPassword", String.class, Object[].class);
        return readPasswordMethod;
      } catch (NoSuchMethodException exception) {
        throw new IllegalArgumentException(
            "Interactive console handle does not expose readPassword(String, Object...).",
            exception);
      }
    }
  }

  private static IllegalStateException noConsole() {
    return new IllegalStateException(NO_INTERACTIVE_CONSOLE_MESSAGE);
  }

  /** Prompt modes for existing-book secrets versus newly entered replacement secrets. */
  enum PromptStyle {
    SINGLE,
    CONFIRMED_NEW_SECRET;

    private String primaryPrompt(Path normalizedPath) {
      return switch (this) {
        case SINGLE -> "FinGrind book passphrase for %s: ".formatted(normalizedPath);
        case CONFIRMED_NEW_SECRET ->
            "New FinGrind book passphrase for %s: ".formatted(normalizedPath);
      };
    }

    private String confirmationPrompt(Path normalizedPath) {
      if (this != CONFIRMED_NEW_SECRET) {
        throw new IllegalStateException("This prompt style does not support confirmation.");
      }
      return "Confirm new FinGrind book passphrase for %s: ".formatted(normalizedPath);
    }
  }
}
