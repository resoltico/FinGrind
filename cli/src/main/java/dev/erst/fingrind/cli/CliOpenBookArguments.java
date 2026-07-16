package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.InventoryCostingDoctrine;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses CLI arguments for `open-book`. */
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
              ProtocolOptions.Presentation.OUTPUT),
          List.of(ProtocolOptions.BookDefinition.TIGHTEN_PARENTS));

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
                resolveBookDoctrine(argumentValues),
                requireFunctionalCurrency(argumentValues.functionalCurrency),
                requireFiscalYearStart(argumentValues.fiscalYearStart))),
        argumentValues.tightenParents,
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
      case ProtocolOptions.BookDefinition.ENTITY_NAME ->
          argumentValues.entityName =
              CliOptionValues.parseBookEntityNameOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.ENTITY_NAME),
                  ProtocolOptions.BookDefinition.ENTITY_NAME);
      case ProtocolOptions.BookDefinition.TEMPLATE_ID ->
          argumentValues.bookTemplateId =
              CliOptionValues.parseBookTemplateIdOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.TEMPLATE_ID),
                  ProtocolOptions.BookDefinition.TEMPLATE_ID);
      case ProtocolOptions.BookDefinition.ACCOUNTING_BASIS ->
          argumentValues.accountingBasis =
              CliOptionValues.parseAccountingBasisOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.ACCOUNTING_BASIS),
                  ProtocolOptions.BookDefinition.ACCOUNTING_BASIS);
      case ProtocolOptions.BookDefinition.INVENTORY_COSTING ->
          argumentValues.inventoryCostingDoctrine =
              CliOptionValues.parseInventoryCostingDoctrineOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.INVENTORY_COSTING),
                  ProtocolOptions.BookDefinition.INVENTORY_COSTING);
      case ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY ->
          argumentValues.functionalCurrency =
              CliOptionValues.parseCurrencyUnitOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY),
                  ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY);
      case ProtocolOptions.BookDefinition.FISCAL_YEAR_START ->
          argumentValues.fiscalYearStart =
              CliOptionValues.parseFiscalYearStartOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.FISCAL_YEAR_START),
                  ProtocolOptions.BookDefinition.FISCAL_YEAR_START);
      case ProtocolOptions.Presentation.OUTPUT ->
          argumentValues.outputMode =
              CliOptionModes.requireOutputMode(
                  argumentValues.outputMode,
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.Presentation.OUTPUT),
                  CliOptionModes.supportedOutputModes(OutputMode.JSON, OutputMode.TEXT));
      case ProtocolOptions.BookDefinition.TIGHTEN_PARENTS -> argumentValues.tightenParents = true;
      default ->
          throw CliArgumentValueParser.unsupportedArgument(
              argument,
              List.of(
                  ProtocolOptions.BookDefinition.ENTITY_NAME,
                  ProtocolOptions.BookDefinition.TEMPLATE_ID,
                  ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
                  ProtocolOptions.BookDefinition.INVENTORY_COSTING,
                  ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
                  ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
                  ProtocolOptions.BookDefinition.TIGHTEN_PARENTS,
                  ProtocolOptions.Presentation.OUTPUT));
    }
  }

  private static BookEntityName requireEntityName(@Nullable BookEntityName entityName) {
    if (entityName == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.ENTITY_NAME,
          "A " + ProtocolOptions.BookDefinition.ENTITY_NAME + " argument is required.");
    }
    return entityName;
  }

  private static BookTemplateId requireBookTemplateId(@Nullable BookTemplateId bookTemplateId) {
    if (bookTemplateId == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.TEMPLATE_ID,
          "A " + ProtocolOptions.BookDefinition.TEMPLATE_ID + " argument is required.");
    }
    return bookTemplateId;
  }

  private static AccountingBasis requireAccountingBasis(@Nullable AccountingBasis accountingBasis) {
    if (accountingBasis == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.ACCOUNTING_BASIS,
          "A " + ProtocolOptions.BookDefinition.ACCOUNTING_BASIS + " argument is required.");
    }
    return accountingBasis;
  }

  private static dev.erst.fingrind.core.BookDoctrine resolveBookDoctrine(
      OpenBookArgumentValues argumentValues) {
    BookTemplateId bookTemplateId = requireBookTemplateId(argumentValues.bookTemplateId);
    AccountingBasis accountingBasis = requireAccountingBasis(argumentValues.accountingBasis);
    try {
      return BookDoctrines.forTemplateAndBasis(
          bookTemplateId, accountingBasis, argumentValues.inventoryCostingDoctrine);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.INVENTORY_COSTING,
          java.util.Objects.requireNonNullElse(
              exception.getMessage(), "Invalid inventory costing doctrine."),
          exception);
    }
  }

  private static CurrencyUnit requireFunctionalCurrency(@Nullable CurrencyUnit functionalCurrency) {
    if (functionalCurrency == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY,
          "A " + ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY + " argument is required.");
    }
    return functionalCurrency;
  }

  private static FiscalYearStart requireFiscalYearStart(@Nullable FiscalYearStart fiscalYearStart) {
    if (fiscalYearStart == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.FISCAL_YEAR_START,
          "A " + ProtocolOptions.BookDefinition.FISCAL_YEAR_START + " argument is required.");
    }
    return fiscalYearStart;
  }

  /** Accumulates one parsed open-book argument set before required-field resolution runs. */
  static final class OpenBookArgumentValues {
    private @Nullable BookEntityName entityName;
    private @Nullable BookTemplateId bookTemplateId;
    private @Nullable AccountingBasis accountingBasis;
    private @Nullable InventoryCostingDoctrine inventoryCostingDoctrine;
    private @Nullable CurrencyUnit functionalCurrency;
    private @Nullable FiscalYearStart fiscalYearStart;
    private @Nullable OutputMode outputMode;
    private boolean tightenParents;
  }
}
