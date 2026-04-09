package dev.erst.fingrind.cli;

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
      case "preflight-entry" -> parseEntryCommand(arguments, true);
      case "post-entry" -> parseEntryCommand(arguments, false);
      default -> throw unknownCommand(arguments.getFirst());
    };
  }

  private static CliCommand parseSingleToken(List<String> arguments, CliCommand command) {
    if (arguments.size() != 1) {
      throw invalid(arguments.get(1), "This command does not accept additional arguments.");
    }
    return command;
  }

  private static CliCommand parseEntryCommand(List<String> arguments, boolean preflight) {
    Path bookFilePath = null;
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
        case "--request-file" -> {
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
    if (requestFile == null) {
      throw invalid("--request-file", "A --request-file argument is required.");
    }
    if (bookFilePath.toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw invalid(
          "--request-file", "--book-file and --request-file must not point to the same path.");
    }
    return preflight
        ? new CliCommand.PreflightEntry(bookFilePath, requestFile)
        : new CliCommand.PostEntry(bookFilePath, requestFile);
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

  private static CliArgumentsException unknownCommand(String commandName) {
    return new CliArgumentsException(
        "unknown-command",
        commandName,
        "Unsupported command: " + commandName,
        "Run 'fingrind help' to inspect the supported commands and examples.");
  }
}
