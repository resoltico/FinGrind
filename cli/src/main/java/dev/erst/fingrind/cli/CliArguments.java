package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.BookAccess;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/** Parses raw command-line arguments into a typed FinGrind CLI command. */
final class CliArguments {
  private CliArguments() {}

  /** Parses the raw CLI arguments into the corresponding command model. */
  static CliCommand parse(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    List<String> arguments = List.of(args);
    if (arguments.isEmpty()) {
      return new CliCommand.Help();
    }
    return switch (arguments.getFirst()) {
      case "help", "--help", "-h" -> parseSingleToken(arguments, new CliCommand.Help());
      case "version", "--version" -> parseSingleToken(arguments, new CliCommand.Version());
      case "capabilities" -> parseSingleToken(arguments, new CliCommand.Capabilities());
      case "print-request-template", "--print-request-template" ->
          parseSingleToken(arguments, new CliCommand.PrintRequestTemplate());
      case "open-book" -> parseBookOnlyCommand(arguments, CliCommand.OpenBook::new);
      case "rekey-book" -> parseRekeyBookCommand(arguments);
      case "declare-account" -> parseRequestBoundCommand(arguments, CliCommand.DeclareAccount::new);
      case "list-accounts" -> parseBookOnlyCommand(arguments, CliCommand.ListAccounts::new);
      case "preflight-entry" -> parseRequestBoundCommand(arguments, CliCommand.PreflightEntry::new);
      case "post-entry" -> parseRequestBoundCommand(arguments, CliCommand.PostEntry::new);
      default -> throw unknownCommand(arguments.getFirst());
    };
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      throw invalid(arguments.get(1), "This command does not accept additional arguments.");
    }
    return command;
  }

  private static CliCommand parseBookOnlyCommand(
      List<String> arguments, BookOnlyCommandFactory commandFactory) {
    ParsedBookArguments parsedArguments = parseBookArguments(arguments, false);
    return commandFactory.create(parsedArguments.bookAccess());
  }

  private static CliCommand parseRequestBoundCommand(
      List<String> arguments, RequestBoundCommandFactory commandFactory) {
    ParsedBookArguments parsedArguments = parseBookArguments(arguments, true);
    return commandFactory.create(
        parsedArguments.bookAccess(), parsedArguments.optionalRequestFile().orElseThrow());
  }

  private static CliCommand parseRekeyBookCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path currentBookKeyFilePath = null;
    Path replacementBookKeyFilePath = null;
    PassphraseSourceKind currentPassphraseSourceKind = null;
    PassphraseSourceKind replacementPassphraseSourceKind = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case "--book-file" -> {
          if (bookFilePath != null) {
            throw invalid("--book-file", "Duplicate argument: --book-file");
          }
          bookFilePath = Path.of(requireValue(argumentIterator, "--book-file"));
        }
        case "--book-key-file" -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          currentBookKeyFilePath = Path.of(requireValue(argumentIterator, "--book-key-file"));
        }
        case "--book-passphrase-stdin" -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case "--book-passphrase-prompt" -> {
          currentPassphraseSourceKind =
              requireSinglePassphraseSource(
                  currentPassphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case "--new-book-key-file" -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          replacementBookKeyFilePath =
              Path.of(requireValue(argumentIterator, "--new-book-key-file"));
        }
        case "--new-book-passphrase-stdin" -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case "--new-book-passphrase-prompt" -> {
          replacementPassphraseSourceKind =
              requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw invalid("--book-file", "A --book-file argument is required.");
    }
    if (currentPassphraseSourceKind == null) {
      throw invalid(
          "--book-key-file",
          "Exactly one current book passphrase source is required: --book-key-file <path>,"
              + " --book-passphrase-stdin, or --book-passphrase-prompt.");
    }
    if (replacementPassphraseSourceKind == null) {
      throw invalid(
          "--new-book-key-file",
          "Exactly one replacement book passphrase source is required: --new-book-key-file <path>,"
              + " --new-book-passphrase-stdin, or --new-book-passphrase-prompt.");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        passphraseSource(currentPassphraseSourceKind, currentBookKeyFilePath);
    BookAccess.PassphraseSource replacementPassphraseSource =
        passphraseSource(replacementPassphraseSourceKind, replacementBookKeyFilePath);
    validateDistinctRekeyPaths(bookFilePath, currentPassphraseSource, replacementPassphraseSource);
    validateRekeyStandardInputUsage(currentPassphraseSource, replacementPassphraseSource);
    return new CliCommand.RekeyBook(
        new BookAccess(bookFilePath, currentPassphraseSource), replacementPassphraseSource);
  }

  private static ParsedBookArguments parseBookArguments(
      List<String> arguments, boolean requestRequired) {
    Path bookFilePath = null;
    Path bookKeyFilePath = null;
    PassphraseSourceKind passphraseSourceKind = null;
    Path requestFile = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case "--book-file" -> {
          if (bookFilePath != null) {
            throw invalid("--book-file", "Duplicate argument: --book-file");
          }
          bookFilePath = Path.of(requireValue(argumentIterator, "--book-file"));
        }
        case "--book-key-file" -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(passphraseSourceKind, PassphraseSourceKind.KEY_FILE);
          bookKeyFilePath = Path.of(requireValue(argumentIterator, "--book-key-file"));
        }
        case "--book-passphrase-stdin" -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(
                  passphraseSourceKind, PassphraseSourceKind.STANDARD_INPUT);
        }
        case "--book-passphrase-prompt" -> {
          passphraseSourceKind =
              requireSinglePassphraseSource(
                  passphraseSourceKind, PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case "--request-file" -> {
          if (!requestRequired) {
            throw invalid(argument, "Unsupported argument: " + argument);
          }
          if (requestFile != null) {
            throw invalid("--request-file", "Duplicate argument: --request-file");
          }
          requestFile = Path.of(requireValue(argumentIterator, "--request-file"));
        }
        default -> throw invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw invalid("--book-file", "A --book-file argument is required.");
    }
    if (passphraseSourceKind == null) {
      throw invalid(
          "--book-key-file",
          "Exactly one book passphrase source is required: --book-key-file <path>,"
              + " --book-passphrase-stdin, or --book-passphrase-prompt.");
    }
    if (requestRequired && requestFile == null) {
      throw invalid("--request-file", "A --request-file argument is required.");
    }
    BookAccess.PassphraseSource passphraseSource =
        passphraseSource(passphraseSourceKind, bookKeyFilePath);
    validateDistinctPaths(bookFilePath, passphraseSource, requestFile);
    validateStandardInputUsage(passphraseSource, requestFile);
    return new ParsedBookArguments(new BookAccess(bookFilePath, passphraseSource), requestFile);
  }

  private static PassphraseSourceKind requireSinglePassphraseSource(
      PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw invalid(
          candidateSource.optionName(),
          "Exactly one book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  private static PassphraseSourceKind requireSingleReplacementPassphraseSource(
      PassphraseSourceKind currentSource, PassphraseSourceKind candidateSource) {
    Objects.requireNonNull(candidateSource, "candidateSource");
    if (currentSource != null) {
      throw invalid(
          replacementOptionName(candidateSource),
          "Exactly one replacement book passphrase source is permitted per command.");
    }
    return candidateSource;
  }

  private static BookAccess.PassphraseSource passphraseSource(
      PassphraseSourceKind passphraseSourceKind, Path bookKeyFilePath) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE ->
          new BookAccess.PassphraseSource.KeyFile(
              Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath"));
      case STANDARD_INPUT -> BookAccess.PassphraseSource.StandardInput.INSTANCE;
      case INTERACTIVE_PROMPT -> BookAccess.PassphraseSource.InteractivePrompt.INSTANCE;
    };
  }

  private static void validateDistinctPaths(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, Path requestFile) {
    if (passphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile
        && bookFilePath.toAbsolutePath().equals(keyFile.bookKeyFilePath().toAbsolutePath())) {
      throw invalid(
          "--book-key-file", "--book-file and --book-key-file must not point to the same path.");
    }
    if (requestFile != null && bookFilePath.toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw invalid(
          "--request-file", "--book-file and --request-file must not point to the same path.");
    }
    if (requestFile != null
        && passphraseSource instanceof BookAccess.PassphraseSource.KeyFile keyFile
        && keyFile.bookKeyFilePath().toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw invalid(
          "--book-key-file", "--book-key-file and --request-file must not point to the same path.");
    }
  }

  private static void validateStandardInputUsage(
      BookAccess.PassphraseSource passphraseSource, Path requestFile) {
    if (requestFile != null
        && "-".equals(requestFile.toString())
        && passphraseSource instanceof BookAccess.PassphraseSource.StandardInput) {
      throw invalid(
          "--book-passphrase-stdin",
          "Standard input cannot supply both the book passphrase and the request JSON.");
    }
  }

  private static void validateDistinctRekeyPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    validateDistinctPaths(bookFilePath, currentPassphraseSource, null);
    validateDistinctPaths(bookFilePath, replacementPassphraseSource, null);
    if (currentPassphraseSource instanceof BookAccess.PassphraseSource.KeyFile currentKeyFile
        && replacementPassphraseSource
            instanceof BookAccess.PassphraseSource.KeyFile replacementKeyFile
        && currentKeyFile
            .bookKeyFilePath()
            .toAbsolutePath()
            .equals(replacementKeyFile.bookKeyFilePath().toAbsolutePath())) {
      throw invalid(
          "--new-book-key-file",
          "--book-key-file and --new-book-key-file must not point to the same path.");
    }
  }

  private static void validateRekeyStandardInputUsage(
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (currentPassphraseSource instanceof BookAccess.PassphraseSource.StandardInput
        && replacementPassphraseSource instanceof BookAccess.PassphraseSource.StandardInput) {
      throw invalid(
          "--new-book-passphrase-stdin",
          "Standard input cannot supply both the current and replacement book passphrases.");
    }
  }

  private static String requireValue(ListIterator<String> argumentIterator, String optionName) {
    if (!argumentIterator.hasNext()) {
      throw invalid(optionName, "Missing value for " + optionName + ".");
    }
    return argumentIterator.next();
  }

  private static CliArgumentsException invalid(String argument, String message) {
    return new CliArgumentsException(
        "invalid-request",
        argument,
        message,
        "Run 'fingrind help' to inspect the supported command syntax.");
  }

  /** Canonical passphrase-source selections accepted by the CLI parser. */
  private enum PassphraseSourceKind {
    KEY_FILE("--book-key-file"),
    STANDARD_INPUT("--book-passphrase-stdin"),
    INTERACTIVE_PROMPT("--book-passphrase-prompt");

    private final String optionName;

    PassphraseSourceKind(String optionName) {
      this.optionName = optionName;
    }

    private String optionName() {
      return optionName;
    }
  }

  private static String replacementOptionName(PassphraseSourceKind passphraseSourceKind) {
    return switch (Objects.requireNonNull(passphraseSourceKind, "passphraseSourceKind")) {
      case KEY_FILE -> "--new-book-key-file";
      case STANDARD_INPUT -> "--new-book-passphrase-stdin";
      case INTERACTIVE_PROMPT -> "--new-book-passphrase-prompt";
    };
  }

  private static CliArgumentsException unknownCommand(String commandName) {
    return new CliArgumentsException(
        "unknown-command",
        commandName,
        "Unsupported command: " + commandName,
        "Run 'fingrind help' to inspect the supported commands and examples.");
  }

  /** Parsed path arguments shared by commands that address one book file. */
  private record ParsedBookArguments(BookAccess bookAccess, Path requestFile) {
    private ParsedBookArguments {
      Objects.requireNonNull(bookAccess, "bookAccess");
    }

    /** Returns the optional request file when the command expects one. */
    private java.util.Optional<Path> optionalRequestFile() {
      return java.util.Optional.ofNullable(requestFile);
    }
  }

  /** Factory for commands that only need a selected book file. */
  @FunctionalInterface
  private interface BookOnlyCommandFactory {
    /** Creates one CLI command for the provided book path. */
    CliCommand create(BookAccess bookAccess);
  }

  /** Factory for commands that need both a book file and a request payload path. */
  @FunctionalInterface
  private interface RequestBoundCommandFactory {
    /** Creates one CLI command for the provided book and request paths. */
    CliCommand create(BookAccess bookAccess, Path requestFile);
  }
}
