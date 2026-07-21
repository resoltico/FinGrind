package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.util.ListIterator;

/** Parses doctrine, reporting-currency, and effective-date options for opening a book. */
final class CliOpenBookConfigurationArguments {
  private CliOpenBookConfigurationArguments() {}

  static boolean apply(
      CliOpenBookArgumentValues values, String argument, ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.BookDefinition.INVENTORY_COSTING ->
          values.inventoryCostingDoctrine =
              CliOptionValues.parseInventoryCostingDoctrineOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.INVENTORY_COSTING),
                  ProtocolOptions.BookDefinition.INVENTORY_COSTING);
      case ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY ->
          values.functionalCurrency =
              CliOptionValues.parseCurrencyUnitOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY),
                  ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY);
      case ProtocolOptions.BookDefinition.FISCAL_YEAR_START ->
          values.fiscalYearStart =
              CliOptionValues.parseFiscalYearStartOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.FISCAL_YEAR_START),
                  ProtocolOptions.BookDefinition.FISCAL_YEAR_START);
      case ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE ->
          values.bookStartEffectiveDate =
              CliOptionValues.parseLocalDateOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE),
                  ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE);
      default -> {
        return false;
      }
    }
    return true;
  }
}
