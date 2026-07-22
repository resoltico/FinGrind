package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Implements the shared book-access grammar after a command selects its tail options. */
final class CliBookAccessGrammar {
  private CliBookAccessGrammar() {}

  static CliBookArgumentParser.ParsedBookArguments parse(
      List<String> arguments,
      CliBookArgumentMode mode,
      CliBookArgumentParser.@Nullable CommandArgumentSpec commandArgumentSpec) {
    if (mode.collectsCommandArguments()) {
      Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec");
    }
    ParsedBookArgumentValues argumentValues = new ParsedBookArgumentValues();
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      applyBookArgument(
          argumentValues, mode, commandArgumentSpec, argumentIterator.next(), argumentIterator);
    }
    Path bookFilePath = requireBookAccessArguments(argumentValues, mode);
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseParser.passphraseSource(
            argumentValues.passphraseSourceKind, argumentValues.bookKeyFilePath);
    CliBookPathValidator.validateDistinctPaths(
        bookFilePath, passphraseSource, argumentValues.requestFile);
    CliBookPathValidator.validateStandardInputUsage(passphraseSource, argumentValues.requestFile);
    return new CliBookArgumentParser.ParsedBookArguments(
        new BookAccess(
            bookFilePath,
            passphraseSource,
            argumentValues.attestationCredentials.resolveOptional()),
        argumentValues.requestFile,
        argumentValues.commandArguments);
  }

  static List<String> supportedArguments(
      CliBookArgumentMode mode,
      CliBookArgumentParser.@Nullable CommandArgumentSpec commandArgumentSpec) {
    List<String> requiredArguments =
        List.of(
            ProtocolBookAccessOptions.BOOK_FILE,
            ProtocolBookAccessOptions.BOOK_KEY_FILE,
            ProtocolBookAccessOptions.BOOK_PASSPHRASE_STDIN,
            ProtocolBookAccessOptions.BOOK_PASSPHRASE_PROMPT,
            ProtocolOptions.Attestation.CUSTODIAN,
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

  static void requireAttestationCredentials(BookAccess bookAccess) {
    CliAttestationCredentialArguments.requirePresent(bookAccess);
  }

  private static Path requireBookAccessArguments(
      ParsedBookArgumentValues argumentValues, CliBookArgumentMode mode) {
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
    return argumentValues.bookFilePath;
  }

  private static void applyBookArgument(
      ParsedBookArgumentValues argumentValues,
      CliBookArgumentMode mode,
      CliBookArgumentParser.@Nullable CommandArgumentSpec commandArgumentSpec,
      String argument,
      ListIterator<String> argumentIterator) {
    if (mode.collectsCommandArguments()) {
      CliBookArgumentParser.CommandArgumentSpec selectedCommandArgumentSpec =
          Objects.requireNonNull(commandArgumentSpec, "commandArgumentSpec");
      if (selectedCommandArgumentSpec.supports(argument)) {
        collectCommandArgument(
            argumentValues, selectedCommandArgumentSpec, argument, argumentIterator);
        return;
      }
    }
    if (argumentValues.attestationCredentials.apply(argument, argumentIterator)) {
      return;
    }
    switch (argument) {
      case ProtocolBookAccessOptions.BOOK_FILE ->
          argumentValues.bookFilePath =
              requireSinglePath(
                  argumentValues.bookFilePath,
                  argumentIterator,
                  ProtocolBookAccessOptions.BOOK_FILE);
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
      case ProtocolOptions.Request.FILE ->
          applyRequestFileArgument(argumentValues, mode, commandArgumentSpec, argumentIterator);
      default ->
          throw CliArgumentValueParser.unsupportedArgument(
              argument, supportedArguments(mode, commandArgumentSpec));
    }
  }

  private static Path requireSinglePath(
      @Nullable Path currentPath, ListIterator<String> argumentIterator, String optionName) {
    if (currentPath != null) {
      throw CliArgumentValueParser.invalid(optionName, "Duplicate argument: " + optionName);
    }
    return CliOptionValues.requirePathOptionValue(argumentIterator, optionName);
  }

  private static void applyRequestFileArgument(
      ParsedBookArgumentValues argumentValues,
      CliBookArgumentMode mode,
      CliBookArgumentParser.@Nullable CommandArgumentSpec commandArgumentSpec,
      ListIterator<String> argumentIterator) {
    if (!mode.acceptsRequestFile()) {
      throw CliArgumentValueParser.unsupportedArgument(
          ProtocolOptions.Request.FILE, supportedArguments(mode, commandArgumentSpec));
    }
    argumentValues.requestFile =
        requireSinglePath(
            argumentValues.requestFile, argumentIterator, ProtocolOptions.Request.FILE);
  }

  private static void collectCommandArgument(
      ParsedBookArgumentValues argumentValues,
      CliBookArgumentParser.CommandArgumentSpec commandArgumentSpec,
      String argument,
      ListIterator<String> argumentIterator) {
    argumentValues.commandArguments.add(argument);
    if (commandArgumentSpec.requiresValue(argument)) {
      argumentValues.commandArguments.add(CliOptionValues.requireValue(argumentIterator, argument));
    }
  }

  /** Mutable parse accumulator for the shared book-addressed CLI grammar. */
  private static final class ParsedBookArgumentValues {
    private final List<String> commandArguments = new ArrayList<>();
    private @Nullable Path bookFilePath;
    private @Nullable Path bookKeyFilePath;
    private CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind;
    private @Nullable Path requestFile;
    private final CliAttestationCredentialArguments attestationCredentials =
        new CliAttestationCredentialArguments();
  }
}
