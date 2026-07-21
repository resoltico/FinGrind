package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

  static List<String> requestBoundCommandSupportedArguments(
      @Nullable CommandArgumentSpec commandArgumentSpec) {
    return supportedArguments(
        BookArgumentMode.REQUEST_BOUND_WITH_COMMAND_ARGUMENTS, commandArgumentSpec);
  }

  static void requireAttestationCredentials(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (bookAccess.attestationCredentialSources().isEmpty()) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.PRINCIPAL_ID,
          "Provide one through five aligned attestation credential triples: "
              + ProtocolOptions.Attestation.PRINCIPAL_ID
              + ", "
              + ProtocolOptions.Attestation.KEY_FILE
              + ", and "
              + ProtocolOptions.Attestation.PASSPHRASE_FILE
              + ".");
    }
  }

  private static ParsedBookArguments parseBookArguments(
      List<String> arguments,
      BookArgumentMode mode,
      @Nullable CommandArgumentSpec commandArgumentSpec) {
    ParsedBookArgumentValues argumentValues = new ParsedBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyBookArgument(
          argumentValues, mode, commandArgumentSpec, argumentIterator.next(), argumentIterator);
    }
    if (argumentValues.bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_FILE,
          "A " + ProtocolBookAccessOptions.BOOK_FILE + " argument is required.");
    }
    if (argumentValues.passphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolBookAccessOptions.BOOK_KEY_FILE,
          "Exactly one book passphrase source is required: "
              + ProtocolBookAccessOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (mode.acceptsRequestFile() && argumentValues.requestFile == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Request.FILE,
          "A " + ProtocolOptions.Request.FILE + " argument is required.");
    }
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseParser.passphraseSource(
            argumentValues.passphraseSourceKind, argumentValues.bookKeyFilePath);
    CliBookPathValidator.validateDistinctPaths(
        argumentValues.bookFilePath, passphraseSource, argumentValues.requestFile);
    CliBookPathValidator.validateStandardInputUsage(passphraseSource, argumentValues.requestFile);
    return new ParsedBookArguments(
        new BookAccess(
            argumentValues.bookFilePath,
            passphraseSource,
            resolveAttestationCredentialSources(argumentValues)),
        argumentValues.requestFile,
        argumentValues.commandArguments);
  }

  private static void applyBookArgument(
      ParsedBookArgumentValues argumentValues,
      BookArgumentMode mode,
      @Nullable CommandArgumentSpec commandArgumentSpec,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolBookAccessOptions.BOOK_FILE -> {
        if (argumentValues.bookFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolBookAccessOptions.BOOK_FILE,
              "Duplicate argument: " + ProtocolBookAccessOptions.BOOK_FILE);
        }
        argumentValues.bookFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolBookAccessOptions.BOOK_FILE);
      }
      case ProtocolBookAccessOptions.BOOK_KEY_FILE -> {
        argumentValues.passphraseSourceKind =
            CliBookPassphraseParser.requireSinglePassphraseSource(
                argumentValues.passphraseSourceKind,
                CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
        argumentValues.bookKeyFilePath =
            CliOptionValues.requirePathOptionValue(
                argumentIterator, ProtocolBookAccessOptions.BOOK_KEY_FILE);
      }
      case ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN ->
          argumentValues.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
      case ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT ->
          argumentValues.passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  argumentValues.passphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
      case ProtocolOptions.Attestation.PRINCIPAL_ID ->
          argumentValues.attestationPrincipalIds.add(
              CliArgumentValueParser.requireValidArgument(
                  ProtocolOptions.Attestation.PRINCIPAL_ID,
                  () ->
                      UUID.fromString(
                          CliOptionValues.requireValue(
                              argumentIterator, ProtocolOptions.Attestation.PRINCIPAL_ID))));
      case ProtocolOptions.Attestation.KEY_FILE ->
          argumentValues.attestationKeyFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.KEY_FILE));
      case ProtocolOptions.Attestation.PASSPHRASE_FILE ->
          argumentValues.attestationPassphraseFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.PASSPHRASE_FILE));
      case ProtocolOptions.Request.FILE ->
          applyRequestFileArgument(argumentValues, mode, commandArgumentSpec, argumentIterator);
      default ->
          applyCommandArgument(
              argumentValues, mode, commandArgumentSpec, argument, argumentIterator);
    }
  }

  private static List<AttestationCredentialSource> resolveAttestationCredentialSources(
      ParsedBookArgumentValues argumentValues) {
    int count = argumentValues.attestationPrincipalIds.size();
    if (count == 0
        && argumentValues.attestationKeyFiles.isEmpty()
        && argumentValues.attestationPassphraseFiles.isEmpty()) {
      return List.of();
    }
    if (count == 0
        || count > 5
        || argumentValues.attestationKeyFiles.size() != count
        || argumentValues.attestationPassphraseFiles.size() != count) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.PRINCIPAL_ID,
          "Provide one through five aligned attestation credential triples: "
              + ProtocolOptions.Attestation.PRINCIPAL_ID
              + ", "
              + ProtocolOptions.Attestation.KEY_FILE
              + ", and "
              + ProtocolOptions.Attestation.PASSPHRASE_FILE
              + ".");
    }
    List<AttestationCredentialSource> sources = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      sources.add(
          new AttestationCredentialSource(
              argumentValues.attestationPrincipalIds.get(index),
              argumentValues.attestationKeyFiles.get(index),
              argumentValues.attestationPassphraseFiles.get(index)));
    }
    return List.copyOf(sources);
  }

  private static void applyRequestFileArgument(
      ParsedBookArgumentValues argumentValues,
      BookArgumentMode mode,
      @Nullable CommandArgumentSpec commandArgumentSpec,
      ListIterator<String> argumentIterator) {
    if (!mode.acceptsRequestFile()) {
      throw CliArgumentValueParser.unsupportedArgument(
          ProtocolOptions.Request.FILE, supportedArguments(mode, commandArgumentSpec));
    }
    if (argumentValues.requestFile != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Request.FILE, "Duplicate argument: " + ProtocolOptions.Request.FILE);
    }
    argumentValues.requestFile =
        CliOptionValues.requirePathOptionValue(argumentIterator, ProtocolOptions.Request.FILE);
  }

  private static void applyCommandArgument(
      ParsedBookArgumentValues argumentValues,
      BookArgumentMode mode,
      @Nullable CommandArgumentSpec commandArgumentSpec,
      String argument,
      ListIterator<String> argumentIterator) {
    if (!mode.collectsCommandArguments()) {
      throw CliArgumentValueParser.unsupportedArgument(
          argument, supportedArguments(mode, commandArgumentSpec));
    }
    CommandArgumentSpec requiredCommandArgumentSpec =
        Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec");
    if (!requiredCommandArgumentSpec.supports(argument)) {
      throw CliArgumentValueParser.unsupportedArgument(
          argument, supportedArguments(mode, requiredCommandArgumentSpec));
    }
    argumentValues.commandArguments.add(argument);
    if (requiredCommandArgumentSpec.requiresValue(argument)) {
      argumentValues.commandArguments.add(CliOptionValues.requireValue(argumentIterator, argument));
    }
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

  /** Mutable parse accumulator for the shared book-addressed CLI grammar. */
  private static final class ParsedBookArgumentValues {
    private final List<String> commandArguments = new ArrayList<>();
    private @Nullable Path bookFilePath;
    private @Nullable Path bookKeyFilePath;
    private CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind;
    private @Nullable Path requestFile;
    private final List<UUID> attestationPrincipalIds = new ArrayList<>();
    private final List<Path> attestationKeyFiles = new ArrayList<>();
    private final List<Path> attestationPassphraseFiles = new ArrayList<>();
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

  private static List<String> supportedArguments(
      BookArgumentMode mode, @Nullable CommandArgumentSpec commandArgumentSpec) {
    List<String> requiredArguments =
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
            ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT,
            ProtocolOptions.Attestation.PRINCIPAL_ID,
            ProtocolOptions.Attestation.KEY_FILE,
            ProtocolOptions.Attestation.PASSPHRASE_FILE);
    List<String> supportedArguments = new ArrayList<>(requiredArguments);
    if (mode.acceptsRequestFile()) {
      supportedArguments.add(ProtocolOptions.Request.FILE);
    }
    if (mode.collectsCommandArguments() && commandArgumentSpec != null) {
      supportedArguments.addAll(commandArgumentSpec.options().keySet());
    }
    return List.copyOf(supportedArguments);
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
