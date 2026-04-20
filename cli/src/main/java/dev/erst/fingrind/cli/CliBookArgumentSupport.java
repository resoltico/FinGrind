package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared book-file and passphrase-source parsing for CLI commands that address one book. */
final class CliBookArgumentSupport {
  private CliBookArgumentSupport() {}

  static ParsedBookArguments parseRequestBoundArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.REQUEST_BOUND);
  }

  static ParsedBookArguments parseRequestBoundCommandArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.REQUEST_BOUND_WITH_COMMAND_ARGUMENTS);
  }

  static ParsedBookArguments parseBookAndCommandArguments(List<String> arguments) {
    return parseBookArguments(arguments, BookArgumentMode.BOOK_WITH_COMMAND_ARGUMENTS);
  }

  private static ParsedBookArguments parseBookArguments(
      List<String> arguments, BookArgumentMode mode) {
    Path bookFilePath = null;
    Path bookKeyFilePath = null;
    CliBookPassphraseArgumentSupport.PassphraseSourceKind passphraseSourceKind = null;
    Path requestFile = null;
    List<String> commandArguments = new ArrayList<>();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentSupport.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          passphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  passphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.KEY_FILE);
          bookKeyFilePath =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          passphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  passphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          passphraseSourceKind =
              CliBookPassphraseArgumentSupport.requireSinglePassphraseSource(
                  passphraseSourceKind,
                  CliBookPassphraseArgumentSupport.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.REQUEST_FILE -> {
          if (!mode.acceptsRequestFile()) {
            throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
          }
          if (requestFile != null) {
            throw CliArgumentSupport.invalid(
                ProtocolOptions.REQUEST_FILE,
                "Duplicate argument: " + ProtocolOptions.REQUEST_FILE);
          }
          requestFile =
              CliArgumentSupport.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.REQUEST_FILE);
        }
        default -> {
          if (!mode.collectsCommandArguments()) {
            throw CliArgumentSupport.invalid(argument, "Unsupported argument: " + argument);
          }
          commandArguments.add(argument);
        }
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentSupport.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (passphraseSourceKind == null) {
      throw CliArgumentSupport.invalid(
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
      throw CliArgumentSupport.invalid(
          ProtocolOptions.REQUEST_FILE,
          "A " + ProtocolOptions.REQUEST_FILE + " argument is required.");
    }
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseArgumentSupport.passphraseSource(passphraseSourceKind, bookKeyFilePath);
    CliBookPathValidationSupport.validateDistinctPaths(bookFilePath, passphraseSource, requestFile);
    CliBookPathValidationSupport.validateStandardInputUsage(passphraseSource, requestFile);
    return new ParsedBookArguments(
        new BookAccess(bookFilePath, passphraseSource), requestFile, commandArguments);
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

  /** Supported parser shapes for commands that address one selected book file. */
  private enum BookArgumentMode {
    REQUEST_BOUND(true, false),
    REQUEST_BOUND_WITH_COMMAND_ARGUMENTS(true, true),
    BOOK_WITH_COMMAND_ARGUMENTS(false, true);

    private final boolean acceptsRequestFile;
    private final boolean collectsCommandArguments;

    BookArgumentMode(boolean acceptsRequestFile, boolean collectsCommandArguments) {
      this.acceptsRequestFile = acceptsRequestFile;
      this.collectsCommandArguments = collectsCommandArguments;
    }

    boolean acceptsRequestFile() {
      return acceptsRequestFile;
    }

    boolean collectsCommandArguments() {
      return collectsCommandArguments;
    }
  }
}
