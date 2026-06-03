package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `open-book`. */
final class CliOpenBookArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec OPEN_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ENTITY_NAME,
              ProtocolOptions.FUNCTIONAL_CURRENCY,
              ProtocolOptions.FISCAL_YEAR_START,
              ProtocolOptions.OUTPUT),
          List.of());

  private CliOpenBookArguments() {}

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, OPEN_BOOK_ARGUMENTS);
    OpenBookArgumentValues argumentValues =
        parseOpenBookArgumentValues(parsedArguments.commandArguments());
    return new OpenBook(
        parsedArguments.bookAccess(),
        new OpenBookCommand(
            new BookIdentity(
                new EntityProfile(requireEntityName(argumentValues.entityName)),
                BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE,
                requireFunctionalCurrency(argumentValues.functionalCurrency),
                requireFiscalYearStart(argumentValues.fiscalYearStart))),
        CliOptionModes.resolvedOutputMode(argumentValues.outputMode));
  }

  private static OpenBookArgumentValues parseOpenBookArgumentValues(List<String> commandArguments) {
    OpenBookArgumentValues argumentValues = new OpenBookArgumentValues();
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      applyOpenBookArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    return argumentValues;
  }

  static void applyOpenBookArgument(
      OpenBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.ENTITY_NAME ->
          argumentValues.entityName =
              CliOptionValues.parseBookEntityNameOption(
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.ENTITY_NAME),
                  ProtocolOptions.ENTITY_NAME);
      case ProtocolOptions.FUNCTIONAL_CURRENCY ->
          argumentValues.functionalCurrency =
              CliOptionValues.parseCurrencyUnitOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.FUNCTIONAL_CURRENCY),
                  ProtocolOptions.FUNCTIONAL_CURRENCY);
      case ProtocolOptions.FISCAL_YEAR_START ->
          argumentValues.fiscalYearStart =
              CliOptionValues.parseFiscalYearStartOption(
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.FISCAL_YEAR_START),
                  ProtocolOptions.FISCAL_YEAR_START);
      case ProtocolOptions.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      default ->
          throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
  }

  private static BookEntityName requireEntityName(@Nullable BookEntityName entityName) {
    if (entityName == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ENTITY_NAME,
          "A " + ProtocolOptions.ENTITY_NAME + " argument is required.");
    }
    return entityName;
  }

  private static CurrencyUnit requireFunctionalCurrency(@Nullable CurrencyUnit functionalCurrency) {
    if (functionalCurrency == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.FUNCTIONAL_CURRENCY,
          "A " + ProtocolOptions.FUNCTIONAL_CURRENCY + " argument is required.");
    }
    return functionalCurrency;
  }

  private static FiscalYearStart requireFiscalYearStart(@Nullable FiscalYearStart fiscalYearStart) {
    if (fiscalYearStart == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.FISCAL_YEAR_START,
          "A " + ProtocolOptions.FISCAL_YEAR_START + " argument is required.");
    }
    return fiscalYearStart;
  }

  /** Accumulates one parsed open-book argument set before required-field resolution runs. */
  static final class OpenBookArgumentValues {
    private @Nullable BookEntityName entityName;
    private @Nullable CurrencyUnit functionalCurrency;
    private @Nullable FiscalYearStart fiscalYearStart;
    private @Nullable OutputMode outputMode;
  }
}
