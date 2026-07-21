package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.ListIterator;

/** Parses entity-name, template, and accounting-basis options for opening a book. */
final class CliOpenBookIdentityArguments {
  private CliOpenBookIdentityArguments() {}

  static boolean apply(
      CliOpenBookArgumentValues values, String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.BookDefinition.ENTITY_NAME ->
          values.entityName =
              CliOptionValues.parseBookEntityNameOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.ENTITY_NAME),
                  ProtocolOptions.BookDefinition.ENTITY_NAME);
      case ProtocolOptions.BookDefinition.TEMPLATE_ID ->
          values.bookTemplateId =
              CliOptionValues.parseBookTemplateIdOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.TEMPLATE_ID),
                  ProtocolOptions.BookDefinition.TEMPLATE_ID);
      case ProtocolOptions.BookDefinition.ACCOUNTING_BASIS ->
          values.accountingBasis =
              CliOptionValues.parseAccountingBasisOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.ACCOUNTING_BASIS),
                  ProtocolOptions.BookDefinition.ACCOUNTING_BASIS);
      default -> {
        return false;
      }
    }
    return true;
  }
}
