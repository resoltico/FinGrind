package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.List;
import java.util.ListIterator;

/** Parses the open-book-specific command tail after protected-book access is parsed. */
final class CliOpenBookArgumentGrammar {
  private static final List<String> SUPPORTED_ARGUMENTS =
      List.of(
          ProtocolOptions.BookDefinition.ENTITY_NAME,
          ProtocolOptions.BookDefinition.TEMPLATE_ID,
          ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
          ProtocolOptions.BookDefinition.INVENTORY_COSTING,
          ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
          ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
          ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
          ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
          ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
          ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
          ProtocolOptions.BookDefinition.TIGHTEN_PARENTS,
          ProtocolOptions.Presentation.OUTPUT);

  private CliOpenBookArgumentGrammar() {}

  static CliOpenBookArgumentValues parse(List<String> commandArguments) {
    CliOpenBookArgumentValues values = new CliOpenBookArgumentValues();
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      apply(values, argumentIterator.next(), argumentIterator);
    }
    return values;
  }

  static void apply(
      CliOpenBookArgumentValues values, String argument, ListIterator<String> argumentIterator) {
    if (ProtocolOptions.BookDefinition.TIGHTEN_PARENTS.equals(argument)) {
      values.tightenParents = true;
      return;
    }
    if (CliOpenBookIdentityArguments.apply(values, argument, argumentIterator)
        || CliOpenBookConfigurationArguments.apply(values, argument, argumentIterator)
        || CliOpenBookFounderArguments.apply(values, argument, argumentIterator)) {
      return;
    }
    throw CliArgumentValueParser.unsupportedArgument(argument, SUPPORTED_ARGUMENTS);
  }
}
