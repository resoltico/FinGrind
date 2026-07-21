package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.EntityProfile;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Resolves validated open-book command-tail state into the lifecycle command. */
final class CliOpenBookCommandFactory {
  private CliOpenBookCommandFactory() {}

  static OpenBook create(
      CliBookArgumentParser.ParsedBookArguments parsedArguments, CliOpenBookArgumentValues values) {
    return new OpenBook(
        parsedArguments.bookAccess(),
        new OpenBookCommand(
            new BookIdentity(
                new EntityProfile(
                    require(values.entityName, ProtocolOptions.BookDefinition.ENTITY_NAME)),
                resolveBookDoctrine(values),
                require(
                    values.functionalCurrency, ProtocolOptions.BookDefinition.FUNCTIONAL_CURRENCY),
                require(values.fiscalYearStart, ProtocolOptions.BookDefinition.FISCAL_YEAR_START),
                require(
                    values.bookStartEffectiveDate,
                    ProtocolOptions.BookDefinition.BOOK_START_EFFECTIVE_DATE)),
            resolveFounders(values)),
        values.tightenParents,
        CliOptionModes.resolvedOutputMode(values.outputMode));
  }

  private static dev.erst.fingrind.core.BookDoctrine resolveBookDoctrine(
      CliOpenBookArgumentValues values) {
    BookTemplateId bookTemplateId =
        require(values.bookTemplateId, ProtocolOptions.BookDefinition.TEMPLATE_ID);
    AccountingBasis accountingBasis =
        require(values.accountingBasis, ProtocolOptions.BookDefinition.ACCOUNTING_BASIS);
    try {
      return BookDoctrines.forTemplateAndBasis(
          bookTemplateId, accountingBasis, values.inventoryCostingDoctrine);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BookDefinition.INVENTORY_COSTING,
          java.util.Objects.requireNonNullElse(
              exception.getMessage(), "Invalid inventory costing doctrine."),
          exception);
    }
  }

  private static List<AttestationFounderInput> resolveFounders(CliOpenBookArgumentValues values) {
    int founderCount = values.founderPrincipalIds.size();
    if (founderCount == 0) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID,
          "At least one "
              + ProtocolOptions.Attestation.FOUNDER_PRINCIPAL_ID
              + " argument is required to establish attested book authorization.");
    }
    if (founderCount > 5
        || values.founderKeyFiles.size() != founderCount
        || values.founderPassphraseFiles.size() != founderCount) {
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
    List<AttestationFounderInput> founders = new ArrayList<>(founderCount);
    for (int index = 0; index < founderCount; index++) {
      founders.add(
          new AttestationFounderInput(
              values.founderPrincipalIds.get(index),
              values.founderKeyFiles.get(index),
              values.founderPassphraseFiles.get(index)));
    }
    return List.copyOf(founders);
  }

  private static <T> T require(@Nullable T value, String optionName) {
    if (value == null) {
      throw CliArgumentValueParser.invalid(
          optionName, "A " + optionName + " argument is required.");
    }
    return value;
  }
}
