package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;

/** Coordinates the shared book-access and open-book command-tail grammars. */
final class CliOpenBookArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec OPEN_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.BookDefinition.ENTITY_NAME,
              ProtocolOptions.BookDefinition.TEMPLATE_ID,
              ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
              ProtocolOptions.BookDefinition.INVENTORY_COSTING,
              ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
              ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
              ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
              ProtocolOptions.Attestation.CUSTODIAN,
              ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
              ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
              ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
              ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.BookDefinition.TIGHTEN_PARENTS));

  private CliOpenBookArguments() {}

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, OPEN_BOOK_ARGUMENTS);
    return CliOpenBookCommandFactory.create(
        parsedArguments, CliOpenBookArgumentGrammar.parse(parsedArguments.commandArguments()));
  }
}
