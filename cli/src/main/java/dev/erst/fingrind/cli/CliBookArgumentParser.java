package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared book-file and passphrase-source parsing for CLI commands that address one book. */
final class CliBookArgumentParser {
  private CliBookArgumentParser() {}

  static ParsedBookArguments parseRequestBoundArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.REQUEST_BOUND, null);
  }

  static ParsedBookArguments parseRequestBoundCommandArguments(
      List<String> arguments, CommandArgumentSpec commandArgumentSpec) {
    Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec");
    return parseBookArguments(
        arguments, BookArgumentMode.REQUEST_BOUND_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  static ParsedBookArguments parseBookAndCommandArguments(
      List<String> arguments, CommandArgumentSpec commandArgumentSpec) {
    Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec");
    return parseBookArguments(
        arguments, BookArgumentMode.BOOK_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  private static ParsedBookArguments parseBookArguments(
      List<String> arguments,
      BookArgumentMode mode,
      @Nullable CommandArgumentSpec commandArgumentSpec) {
    Path bookFilePath = null;
    Path bookKeyFilePath = null;
    CliBookPassphraseParser.PassphraseSourceKind passphraseSourceKind = null;
    Path requestFile = null;
    List<String> commandArguments = new ArrayList<>();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  passphraseSourceKind, CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
          bookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.REQUEST_FILE -> {
          if (!mode.acceptsRequestFile()) {
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
          }
          if (requestFile != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.REQUEST_FILE,
                "Duplicate argument: " + ProtocolOptions.REQUEST_FILE);
          }
          requestFile =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.REQUEST_FILE);
        }
        default -> {
          if (!mode.collectsCommandArguments()) {
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
          }
          if (!Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec")
              .supports(argument)) {
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
          }
          commandArguments.add(argument);
          if (commandArgumentSpec.requiresValue(argument)) {
            commandArguments.add(CliArgumentValueParser.requireValue(argumentIterator, argument));
          }
        }
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (passphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (mode.acceptsRequestFile() && requestFile == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.REQUEST_FILE,
          "A " + ProtocolOptions.REQUEST_FILE + " argument is required.");
    }
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseParser.passphraseSource(passphraseSourceKind, bookKeyFilePath);
    CliBookPathValidator.validateDistinctPaths(bookFilePath, passphraseSource, requestFile);
    CliBookPathValidator.validateStandardInputUsage(passphraseSource, requestFile);
    return new ParsedBookArguments(
        new BookAccess(bookFilePath, passphraseSource), requestFile, commandArguments);
  }

  static CommandArgumentSpec commandArgumentSpec(
      List<String> valueOptions, List<String> flagOptions) {
    return new CommandArgumentSpec(valueOptions, flagOptions);
  }

  /** Parsed path arguments shared by commands that address one book file. */
  record ParsedBookArguments(
      BookAccess bookAccess, @Nullable Path requestFile, List<String> commandArguments) {
    ParsedBookArguments {
      Objects.requireNonNull(bookAccess, "bookAccess");
      commandArguments = List.copyOf(Objects.requireNonNull(commandArguments, "commandArguments"));
    }

    Optional<Path> optionalRequestFile() {
      return Optional.ofNullable(requestFile);
    }
  }

  /** Allowed command-specific tail arguments for book-addressed commands. */
  record CommandArgumentSpec(List<String> valueOptions, List<String> flagOptions) {
    CommandArgumentSpec {
      valueOptions = List.copyOf(Objects.requireNonNull(valueOptions, "valueOptions"));
      flagOptions = List.copyOf(Objects.requireNonNull(flagOptions, "flagOptions"));
    }

    boolean supports(String argument) {
      return valueOptions.contains(argument) || flagOptions.contains(argument);
    }

    boolean requiresValue(String argument) {
      return valueOptions.contains(argument);
    }
  }

  /** Supported parser shapes for commands that address one selected book file. */
  private enum BookArgumentMode {
    REQUEST_BOUND {
      @Override
      boolean acceptsRequestFile() {
        return true;
      }

      @Override
      boolean collectsCommandArguments() {
        return false;
      }
    },
    REQUEST_BOUND_WITH_COMMAND_ARGUMENTS {
      @Override
      boolean acceptsRequestFile() {
        return true;
      }

      @Override
      boolean collectsCommandArguments() {
        return true;
      }
    },
    BOOK_WITH_COMMAND_ARGUMENTS {
      @Override
      boolean acceptsRequestFile() {
        return false;
      }

      @Override
      boolean collectsCommandArguments() {
        return true;
      }
    };

    abstract boolean acceptsRequestFile();

    abstract boolean collectsCommandArguments();
  }
}
