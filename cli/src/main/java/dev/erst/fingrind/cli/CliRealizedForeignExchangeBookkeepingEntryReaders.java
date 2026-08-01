package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import tools.jackson.databind.node.ObjectNode;

/** Reads typed request payloads owned by the Realized Foreign Exchange context. */
final class CliRealizedForeignExchangeBookkeepingEntryReaders {
  private CliRealizedForeignExchangeBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FOREIGN_CURRENCY_OBLIGATION -> readForeignCurrencyObligation(rootNode);
      case REALIZED_FOREIGN_EXCHANGE_SETTLEMENT -> readSettlement(rootNode);
      default ->
          throw new IllegalArgumentException("Expected a realized foreign-exchange entry kind.");
    };
  }

  static RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
      readForeignCurrencyObligation(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(
            BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION));
    return new RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        foreignCurrencyObligationId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE),
        accountCode(
            rootNode,
            ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_GAIN_ACCOUNT_CODE),
        accountCode(
            rootNode,
            ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_LOSS_ACCOUNT_CODE),
        CliBookkeepingEntryNestedParser.requiredForeignExchange(rootNode));
  }

  static RealizedForeignExchangeBookkeepingEntryVariants.Settlement readSettlement(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(
            BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT));
    return new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        foreignCurrencyObligationId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryNestedParser.requiredForeignExchange(rootNode),
        null);
  }

  private static ForeignCurrencyObligationId foreignCurrencyObligationId(ObjectNode rootNode) {
    return new ForeignCurrencyObligationId(
        requiredText(
            rootNode,
            ProtocolBusinessEventFields.RealizedForeignExchange.FOREIGN_CURRENCY_OBLIGATION_ID));
  }

  private static AccountCode accountCode(ObjectNode rootNode, String fieldName) {
    return new AccountCode(requiredText(rootNode, fieldName));
  }
}
