package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Public grammar entry points for CLI commands that address one protected book. */
final class CliBookArgumentParser {
  private CliBookArgumentParser() {}

  static ParsedBookArguments parseRequestBoundArguments(List<String> arguments) {
    return CliBookAccessGrammar.parse(arguments, CliBookArgumentMode.REQUEST_BOUND, null);
  }

  static ParsedBookArguments parseRequestBoundCommandArguments(
      List<String> arguments, CommandArgumentSpec commandArgumentSpec) {
    return CliBookAccessGrammar.parse(
        arguments, CliBookArgumentMode.REQUEST_BOUND_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  static ParsedBookArguments parseBookAndCommandArguments(
      List<String> arguments, CommandArgumentSpec commandArgumentSpec) {
    return CliBookAccessGrammar.parse(
        arguments, CliBookArgumentMode.BOOK_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  static List<String> requestBoundCommandSupportedArguments(
      @Nullable CommandArgumentSpec commandArgumentSpec) {
    return CliBookAccessGrammar.supportedArguments(
        CliBookArgumentMode.REQUEST_BOUND_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  static void requireAttestationCredentials(BookAccess bookAccess) {
    CliBookAccessGrammar.requireAttestationCredentials(bookAccess);
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
}
