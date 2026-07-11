package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingNestedFieldSets;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses nested bookkeeping-entry adjunct objects such as tax, FX, and reversal metadata. */
final class CliBookkeepingEntryNestedParser {
  private CliBookkeepingEntryNestedParser() {}

  static @org.jspecify.annotations.Nullable TaxSelection optionalTaxSelection(ObjectNode rootNode) {
    JsonNode taxNode = rootNode.get(ProtocolPostEntryFields.TopLevel.TAX);
    if (taxNode == null || taxNode.isNull()) {
      return null;
    }
    ObjectNode taxObject = requireObjectNode(taxNode, ProtocolPostEntryFields.TopLevel.TAX);
    rejectUnexpectedFields(
        taxObject,
        ProtocolPostEntryFields.TopLevel.TAX,
        ProtocolPostingNestedFieldSets.taxFields());
    return new TaxSelection(
        new TaxRegistrationId(
            requiredText(taxObject, ProtocolPostEntryFields.Tax.TAX_REGISTRATION_ID)),
        new TaxCode(requiredText(taxObject, ProtocolPostEntryFields.Tax.TAX_CODE)));
  }

  static @org.jspecify.annotations.Nullable ForeignExchangeDetails optionalForeignExchange(
      ObjectNode rootNode) {
    JsonNode foreignExchangeNode = rootNode.get(ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE);
    if (foreignExchangeNode == null || foreignExchangeNode.isNull()) {
      return null;
    }
    ObjectNode foreignExchangeObject =
        requireObjectNode(foreignExchangeNode, ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE);
    rejectUnexpectedFields(
        foreignExchangeObject,
        ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
        ProtocolPostingNestedFieldSets.foreignExchangeFields());
    ObjectNode quotedRateObject =
        requiredObject(foreignExchangeObject, ProtocolPostEntryFields.ForeignExchange.QUOTED_RATE);
    rejectUnexpectedFields(
        quotedRateObject,
        ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE
            + "."
            + ProtocolPostEntryFields.ForeignExchange.QUOTED_RATE,
        ProtocolPostingNestedFieldSets.quotedRateFields());
    return new ForeignExchangeDetails(
        monetaryAmount(
            foreignExchangeObject, ProtocolPostEntryFields.ForeignExchange.TRANSACTION_AMOUNT),
        monetaryAmount(
            foreignExchangeObject, ProtocolPostEntryFields.ForeignExchange.FUNCTIONAL_AMOUNT),
        new QuotedExchangeRate(
            monetaryAmount(
                quotedRateObject, ProtocolPostEntryFields.QuotedRate.TRANSACTION_CURRENCY_AMOUNT),
            monetaryAmount(
                quotedRateObject, ProtocolPostEntryFields.QuotedRate.FUNCTIONAL_CURRENCY_AMOUNT),
            CanonicalTemporalText.parseLocalDate(
                requiredText(quotedRateObject, ProtocolPostEntryFields.QuotedRate.QUOTED_ON),
                ProtocolPostEntryFields.QuotedRate.QUOTED_ON),
            requiredText(quotedRateObject, ProtocolPostEntryFields.QuotedRate.QUOTE_SOURCE)),
        parseWireValue(
            requiredText(
                foreignExchangeObject, ProtocolPostEntryFields.ForeignExchange.TREATMENT_KIND),
            ProtocolPostEntryFields.ForeignExchange.TREATMENT_KIND,
            ForeignExchangeTreatmentKind.wireValues(),
            ForeignExchangeTreatmentKind::fromWireValue));
  }

  static @org.jspecify.annotations.Nullable SettlementAdjunct optionalSettlementAdjunct(
      ObjectNode rootNode) {
    JsonNode settlementAdjunctNode =
        rootNode.get(ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT);
    if (settlementAdjunctNode == null || settlementAdjunctNode.isNull()) {
      return null;
    }
    ObjectNode settlementAdjunctObject =
        requireObjectNode(
            settlementAdjunctNode, ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT);
    rejectUnexpectedFields(
        settlementAdjunctObject,
        ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
        ProtocolPostingNestedFieldSets.settlementAdjunctFields());
    return new SettlementAdjunct(
        new AccountCode(
            requiredText(
                settlementAdjunctObject, ProtocolPostEntryFields.SettlementAdjunct.ACCOUNT_CODE)),
        monetaryAmount(settlementAdjunctObject, ProtocolPostEntryFields.SettlementAdjunct.AMOUNT));
  }

  static @org.jspecify.annotations.Nullable InventoryRelief optionalInventoryRelief(
      ObjectNode rootNode) {
    JsonNode inventoryReliefNode = rootNode.get(ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF);
    if (inventoryReliefNode == null || inventoryReliefNode.isNull()) {
      return null;
    }
    ObjectNode inventoryReliefObject =
        requireObjectNode(inventoryReliefNode, ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF);
    rejectUnexpectedFields(
        inventoryReliefObject,
        ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
        ProtocolPostingNestedFieldSets.inventoryReliefFields());
    return new InventoryRelief(
        new AccountCode(
            requiredText(
                inventoryReliefObject,
                ProtocolPostEntryFields.InventoryRelief.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(
                inventoryReliefObject,
                ProtocolPostEntryFields.InventoryRelief.COST_OF_SALES_ACCOUNT_CODE)),
        new QuantityText(
            requiredText(inventoryReliefObject, ProtocolPostEntryFields.InventoryRelief.QUANTITY)));
  }

  static PostingLineage.Reversal readRequiredReversal(ObjectNode rootNode) {
    ObjectNode reversalObject = requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.REVERSAL);
    return readReversalObject(reversalObject);
  }

  private static MonetaryAmount monetaryAmount(ObjectNode rootNode, String fieldName) {
    return MonetaryAmount.of(CliJsonMoneyParser.requiredPositiveMoney(rootNode, fieldName).money());
  }

  private static PostingLineage.Reversal readReversalObject(ObjectNode reversalObject) {
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolPostEntryFields.TopLevel.REVERSAL,
        ProtocolPostingNestedFieldSets.reversalFields());
    return new PostingLineage.Reversal(
        new ReversalReference(
            new PostingId(
                requiredText(reversalObject, ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID))),
        new ReversalReason(requiredText(reversalObject, ProtocolPostEntryFields.Reversal.REASON)));
  }
}
