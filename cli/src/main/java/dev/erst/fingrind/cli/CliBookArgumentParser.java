package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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
    var options = new LinkedHashMap<String, OptionArity>();
    registerOptions(options, valueOptions, OptionArity.VALUE);
    registerOptions(options, flagOptions, OptionArity.FLAG);
    return new CommandArgumentSpec(options);
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
  record CommandArgumentSpec(Map<String, OptionArity> options) {
    CommandArgumentSpec {
      options = Map.copyOf(Objects.requireNonNull(options, "options"));
    }

    boolean supports(String argument) {
      return options.containsKey(argument);
    }

    boolean requiresValue(String argument) {
      return options.get(argument) == OptionArity.VALUE;
    }
  }

  private static void registerOptions(
      Map<String, OptionArity> options, List<String> optionNames, OptionArity arity) {
    Objects.requireNonNull(options, "options");
    List<String> normalizedOptionNames =
        List.copyOf(Objects.requireNonNull(optionNames, "optionNames"));
    for (String optionName : normalizedOptionNames) {
      String normalized = Objects.requireNonNull(optionName, "optionNames must not contain nulls.");
      if (options.putIfAbsent(normalized, arity) != null) {
        throw new IllegalArgumentException(
            "Command argument options must not repeat or overlap: " + normalized);
      }
    }
  }

  /** Declares whether one CLI option is a bare flag or requires one following value token. */
  enum OptionArity {
    FLAG,
    VALUE
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
