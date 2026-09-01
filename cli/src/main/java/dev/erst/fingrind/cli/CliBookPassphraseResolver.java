package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.sqlite.SqliteBookKeyFile;
import dev.erst.fingrind.sqlite.SqliteBookPassphrase;
import dev.erst.fingrind.sqlite.SqliteBookPassphraseSourceBytes;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePassphraseResolver;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one CLI-visible passphrase source into one UTF-8 passphrase payload with owned-buffer
 * overwrite.
 */
final class CliBookPassphraseResolver implements SqlitePassphraseResolver {
  private static final String NO_INTERACTIVE_CONSOLE_MESSAGE =
      "FinGrind cannot prompt for a book passphrase because no interactive console is available.";
  private static final String STANDARD_INPUT_SOURCE_LABEL = "standard input";
  private static final String INTERACTIVE_PROMPT_SOURCE_LABEL = "interactive prompt";

  private final InputStream inputStream;
  private final Terminal terminal;
  private final String runtimeDistribution;

  CliBookPassphraseResolver(InputStream inputStream, Terminal terminal) {
    this(inputStream, terminal, FinGrindCli.runtimeDistribution());
  }

  CliBookPassphraseResolver(
      InputStream inputStream, Terminal terminal, String runtimeDistribution) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
    this.terminal = Objects.requireNonNull(terminal, "terminal");
    this.runtimeDistribution = Objects.requireNonNull(runtimeDistribution, "runtimeDistribution");
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
    SqlitePassphraseIntent checkedIntent = Objects.requireNonNull(intent, "intent");
    ContractDecision<SqliteBookPassphrase> resolved =
        resolve(bookFilePath, passphraseSource, promptStyle(checkedIntent));
    return requiresNewSecretPolicy(bookFilePath, checkedIntent)
        ? resolved.fold(SqliteBookPassphrase::requireNewSecretPolicy, ContractDecision::rejected)
        : resolved;
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
          readFromInteractivePrompt(bookFilePath, resolvedPromptStyle(bookFilePath, promptStyle));
    };
  }

  private ContractDecision<SqliteBookPassphrase> readFromStandardInput() {
    try {
      return readStandardInputBytes()
          .fold(
              bytes -> {
                if (bytes.length == 0) {
                  Arrays.fill(bytes, (byte) 0);
                  return ContractDecision.rejected(emptyStandardInputFailure());
                }
                return SqliteBookPassphrase.fromUtf8BytesDecision(
                    STANDARD_INPUT_SOURCE_LABEL, bytes);
              },
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
    try {
      return ContractDecision.accepted(SqliteBookPassphraseSourceBytes.read(inputStream));
    } catch (SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException exception) {
      return ContractDecision.rejected(
          oversizedPassphraseSource(
              "standard input",
              "FinGrind book passphrase input from standard input exceeded the %d-byte limit."
                  .formatted(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES)));
    }
  }

  private ContractDecision<SqliteBookPassphrase> readFromInteractivePrompt(
      Path bookFilePath, PromptStyle promptStyle) {
    Path normalizedPath = bookFilePath.toAbsolutePath().normalize();
    String displayPath = CliTextDisplay.path(normalizedPath);
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
      return SqliteBookPassphrase.fromCharactersDecision(INTERACTIVE_PROMPT_SOURCE_LABEL, password);
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
    return SqliteBookPassphrase.fromCharactersDecision(INTERACTIVE_PROMPT_SOURCE_LABEL, password);
  }

  /** Reads one passphrase from an interactive terminal without echo. */
  @FunctionalInterface
  interface Terminal {
    /** Prompts for one passphrase and returns the entered characters. */
    ContractDecision<char[]> readPassword(String prompt);
  }

  static Terminal systemTerminal() {
    return new PromptingConsoleLookupTerminal(CliPromptingConsoles::systemPromptingConsole);
  }

  /** Terminal adapter that obtains the controlling prompt bridge lazily for each read. */
  static final class PromptingConsoleLookupTerminal implements Terminal {
    private final Supplier<@Nullable CliPromptingConsole> promptingConsoleSupplier;

    PromptingConsoleLookupTerminal(
        Supplier<@Nullable CliPromptingConsole> promptingConsoleSupplier) {
      this.promptingConsoleSupplier =
          Objects.requireNonNull(promptingConsoleSupplier, "promptingConsoleSupplier");
    }

    @Override
    public ContractDecision<char[]> readPassword(String prompt) {
      Objects.requireNonNull(prompt, "prompt");
      CliPromptingConsole promptingConsole = promptingConsoleSupplier.get();
      if (promptingConsole == null) {
        return ContractDecision.rejected(noConsole());
      }
      return new PromptingConsoleTerminal(promptingConsole).readPassword(prompt);
    }
  }

  /** Shared terminal adapter that converts one typed prompt seam into FinGrind decisions. */
  static class PromptingConsoleTerminal implements Terminal {
    private final CliPromptingConsole promptingConsole;

    PromptingConsoleTerminal(CliPromptingConsole promptingConsole) {
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
            + ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES
            + "-byte limit, then rerun the command.",
        null);
  }

  private ContractFailure emptyStandardInputFailure() {
    String message =
        "The FinGrind book passphrase source must contain a non-empty UTF-8 passphrase: "
            + STANDARD_INPUT_SOURCE_LABEL;
    if (FinGrindCli.CONTAINER_RUNTIME_DISTRIBUTION.equals(runtimeDistribution)) {
      return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
          message,
          "If you launched FinGrind through a container, rerun the outer command with attached"
              + " standard input such as '"
              + dev.erst.fingrind.contract.protocol.ProtocolCatalog.distribution()
                  .containerMountedLauncherPrefix()
              + " <command>', or switch to --book-key-file.",
          null);
    }
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        message,
        "Provide one non-empty UTF-8 passphrase through the selected key file, standard input, or interactive prompt route.",
        null);
  }

  private static ContractDecision<SqliteBookPassphrase> rejectedPassphrase(
      ContractFailure failure) {
    return ContractDecision.rejected(failure);
  }

  private static PromptStyle promptStyle(SqlitePassphraseIntent intent) {
    return switch (Objects.requireNonNull(intent, "intent")) {
      case EXISTING_SECRET -> PromptStyle.SINGLE;
      case PLAN_SETUP_SECRET -> PromptStyle.PLAN_SETUP;
      case NEW_SECRET -> PromptStyle.CONFIRMED_NEW_SECRET;
    };
  }

  private static PromptStyle resolvedPromptStyle(Path bookFilePath, PromptStyle promptStyle) {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    return switch (Objects.requireNonNull(promptStyle, "promptStyle")) {
      case SINGLE -> PromptStyle.SINGLE;
      case PLAN_SETUP ->
          Files.exists(bookFilePath, LinkOption.NOFOLLOW_LINKS)
              ? PromptStyle.SINGLE
              : PromptStyle.CONFIRMED_NEW_SECRET;
      case CONFIRMED_NEW_SECRET -> PromptStyle.CONFIRMED_NEW_SECRET;
    };
  }

  private static boolean requiresNewSecretPolicy(Path bookFilePath, SqlitePassphraseIntent intent) {
    return switch (Objects.requireNonNull(intent, "intent")) {
      case EXISTING_SECRET -> false;
      case NEW_SECRET -> true;
      case PLAN_SETUP_SECRET -> Files.notExists(bookFilePath, LinkOption.NOFOLLOW_LINKS);
    };
  }

  /** Prompt modes for existing-book secrets versus newly entered replacement secrets. */
  enum PromptStyle {
    SINGLE,
    PLAN_SETUP,
    CONFIRMED_NEW_SECRET;

    String primaryPrompt(String displayPath) {
      return switch (this) {
        case SINGLE -> "Passphrase for %s: ".formatted(displayPath);
        case PLAN_SETUP -> throw new IllegalStateException("PLAN_SETUP must be resolved first.");
        case CONFIRMED_NEW_SECRET -> "New passphrase for %s: ".formatted(displayPath);
      };
    }

    String confirmationPrompt(String displayPath) {
      if (this != CONFIRMED_NEW_SECRET) {
        throw new IllegalStateException("This prompt style does not support confirmation.");
      }
      return "Confirm new passphrase: ";
    }
  }
}
