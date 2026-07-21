package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
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
import java.time.LocalDate;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
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
              ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
              ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
              ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
              ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
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
                requireFiscalYearStart(argumentValues.fiscalYearStart),
                requireBookStartEffectiveDate(argumentValues.bookStartEffectiveDate)),
            resolveFounders(argumentValues)),
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
      case ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE ->
          argumentValues.bookStartEffectiveDate =
              CliOptionValues.parseLocalDateOption(
                  CliOptionValues.requireValue(
                      argumentIterator, ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE),
                  ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE);
      case ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID ->
          argumentValues.founderPrincipalIds.add(
              CliArgumentValueParser.requireValidArgument(
                  ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                  () ->
                      UUID.fromString(
                          CliOptionValues.requireValue(
                              argumentIterator,
                              ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID))));
      case ProtocolOptions.Attestation.FOUNDER_KEY_FILE ->
          argumentValues.founderKeyFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.FOUNDER_KEY_FILE));
      case ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE ->
          argumentValues.founderPassphraseFiles.add(
              CliOptionValues.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE));
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
                  ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
                  ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
                  ProtocolOptions.Attestation.FOUNDER_KEY_FILE,
                  ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE,
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

  private static LocalDate requireBookStartEffectiveDate(
      @Nullable LocalDate bookStartEffectiveDate) {
    if (bookStartEffectiveDate == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE,
          "A "
              + ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE
              + " argument is required.");
    }
    return bookStartEffectiveDate;
  }

  private static List<AttestationFounderInput> resolveFounders(
      OpenBookArgumentValues argumentValues) {
    int founderCount = argumentValues.founderPrincipalIds.size();
    if (founderCount == 0) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
          "At least one "
              + ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID
              + " argument is required to establish attested book authorization.");
    }
    if (founderCount > 5
        || argumentValues.founderKeyFiles.size() != founderCount
        || argumentValues.founderPassphraseFiles.size() != founderCount) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
          "Provide one through five aligned founder triples: "
              + ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID
              + ", "
              + ProtocolOptions.Attestation.FOUNDER_KEY_FILE
              + ", and "
              + ProtocolOptions.Attestation.FOUNDER_PASSPHRASE_FILE
              + ".");
    }
    java.util.ArrayList<AttestationFounderInput> founders = new java.util.ArrayList<>(founderCount);
    for (int index = 0; index < founderCount; index++) {
      founders.add(
          new AttestationFounderInput(
              argumentValues.founderPrincipalIds.get(index),
              argumentValues.founderKeyFiles.get(index),
              argumentValues.founderPassphraseFiles.get(index)));
    }
    return List.copyOf(founders);
  }

  /** Accumulates one parsed open-book argument set before required-field resolution runs. */
  static final class OpenBookArgumentValues {
    private @Nullable BookEntityName entityName;
    private @Nullable BookTemplateId bookTemplateId;
    private @Nullable AccountingBasis accountingBasis;
    private @Nullable InventoryCostingDoctrine inventoryCostingDoctrine;
    private @Nullable CurrencyUnit functionalCurrency;
    private @Nullable FiscalYearStart fiscalYearStart;
    private @Nullable LocalDate bookStartEffectiveDate;
    private @Nullable OutputMode outputMode;
    private final List<UUID> founderPrincipalIds = new java.util.ArrayList<>();
    private final List<java.nio.file.Path> founderKeyFiles = new java.util.ArrayList<>();
    private final List<java.nio.file.Path> founderPassphraseFiles = new java.util.ArrayList<>();
    private boolean tightenParents;
  }
}
