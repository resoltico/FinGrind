package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolForeignExchangeRequestFields;
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
    JsonNode taxNode = rootNode.get(ProtocolBusinessEventFields.Core.TAX);
    if (taxNode == null || taxNode.isNull()) {
      return null;
    }
    ObjectNode taxObject = requireObjectNode(taxNode, ProtocolBusinessEventFields.Core.TAX);
    rejectUnexpectedFields(
        taxObject,
        ProtocolBusinessEventFields.Core.TAX,
        ProtocolPostingNestedFieldSets.taxFields());
    return new TaxSelection(
        new TaxRegistrationId(
            requiredText(taxObject, ProtocolPostEntryFields.Tax.TAX_REGISTRATION_ID)),
        new TaxCode(requiredText(taxObject, ProtocolPostEntryFields.Tax.TAX_CODE)));
  }

  static @org.jspecify.annotations.Nullable ForeignExchangeDetails optionalForeignExchange(
      ObjectNode rootNode) {
    JsonNode foreignExchangeNode = rootNode.get(ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
    if (foreignExchangeNode == null || foreignExchangeNode.isNull()) {
      return null;
    }
    ObjectNode foreignExchangeObject =
        requireObjectNode(foreignExchangeNode, ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
    rejectUnexpectedFields(
        foreignExchangeObject,
        ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
        ProtocolPostingNestedFieldSets.foreignExchangeFields());
    ObjectNode quotedRateObject =
        requiredObject(
            foreignExchangeObject,
            ProtocolForeignExchangeRequestFields.ForeignExchange.QUOTED_RATE);
    rejectUnexpectedFields(
        quotedRateObject,
        ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE
            + "."
            + ProtocolForeignExchangeRequestFields.ForeignExchange.QUOTED_RATE,
        ProtocolPostingNestedFieldSets.quotedRateFields());
    return new ForeignExchangeDetails(
        monetaryAmount(
            foreignExchangeObject,
            ProtocolForeignExchangeRequestFields.ForeignExchange.TRANSACTION_AMOUNT),
        monetaryAmount(
            foreignExchangeObject,
            ProtocolForeignExchangeRequestFields.ForeignExchange.FUNCTIONAL_AMOUNT),
        new QuotedExchangeRate(
            monetaryAmount(
                quotedRateObject,
                ProtocolForeignExchangeRequestFields.QuotedRate.TRANSACTION_CURRENCY_AMOUNT),
            monetaryAmount(
                quotedRateObject,
                ProtocolForeignExchangeRequestFields.QuotedRate.FUNCTIONAL_CURRENCY_AMOUNT),
            CanonicalTemporalText.parseLocalDate(
                requiredText(
                    quotedRateObject, ProtocolForeignExchangeRequestFields.QuotedRate.QUOTED_ON),
                ProtocolForeignExchangeRequestFields.QuotedRate.QUOTED_ON),
            requiredText(
                quotedRateObject, ProtocolForeignExchangeRequestFields.QuotedRate.QUOTE_SOURCE)),
        parseWireValue(
            requiredText(
                foreignExchangeObject,
                ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND),
            ProtocolForeignExchangeRequestFields.ForeignExchange.TREATMENT_KIND,
            ForeignExchangeTreatmentKind.wireValues(),
            ForeignExchangeTreatmentKind::fromWireValue));
  }

  static ForeignExchangeDetails requiredForeignExchange(ObjectNode rootNode) {
    requiredObject(rootNode, ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
    return java.util.Objects.requireNonNull(
        optionalForeignExchange(rootNode), ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
  }

  static @org.jspecify.annotations.Nullable SettlementAdjunct optionalSettlementAdjunct(
      ObjectNode rootNode) {
    JsonNode settlementAdjunctNode =
        rootNode.get(ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT);
    if (settlementAdjunctNode == null || settlementAdjunctNode.isNull()) {
      return null;
    }
    ObjectNode settlementAdjunctObject =
        requireObjectNode(
            settlementAdjunctNode, ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT);
    rejectUnexpectedFields(
        settlementAdjunctObject,
        ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT,
        ProtocolPostingNestedFieldSets.settlementAdjunctFields());
    return new SettlementAdjunct(
        new AccountCode(
            requiredText(
                settlementAdjunctObject, ProtocolPostEntryFields.SettlementAdjunct.ACCOUNT_CODE)),
        monetaryAmount(settlementAdjunctObject, ProtocolPostEntryFields.SettlementAdjunct.AMOUNT));
  }

  static @org.jspecify.annotations.Nullable InventoryRelief optionalInventoryRelief(
      ObjectNode rootNode) {
    JsonNode inventoryReliefNode =
        rootNode.get(ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF);
    if (inventoryReliefNode == null || inventoryReliefNode.isNull()) {
      return null;
    }
    ObjectNode inventoryReliefObject =
        requireObjectNode(
            inventoryReliefNode, ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF);
    rejectUnexpectedFields(
        inventoryReliefObject,
        ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
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

  static AccrualCutoffRecognitionInterval requiredRecognitionInterval(ObjectNode rootNode) {
    ObjectNode intervalObject =
        requiredObject(rootNode, ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL);
    rejectUnexpectedFields(
        intervalObject,
        ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL,
        ProtocolPostingNestedFieldSets.recognitionIntervalFields());
    return new AccrualCutoffRecognitionInterval(
        CanonicalTemporalText.parseLocalDate(
            requiredText(intervalObject, ProtocolPostEntryFields.RecognitionInterval.START_DATE),
            ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL
                + "."
                + ProtocolPostEntryFields.RecognitionInterval.START_DATE),
        CanonicalTemporalText.parseLocalDate(
            requiredText(intervalObject, ProtocolPostEntryFields.RecognitionInterval.END_DATE),
            ProtocolBusinessEventFields.AccrualCutoff.RECOGNITION_INTERVAL
                + "."
                + ProtocolPostEntryFields.RecognitionInterval.END_DATE));
  }

  static PostingLineage.Reversal readRequiredReversal(ObjectNode rootNode) {
    ObjectNode reversalObject = requiredObject(rootNode, ProtocolBusinessEventFields.Core.REVERSAL);
    return readReversalObject(reversalObject);
  }

  private static MonetaryAmount monetaryAmount(ObjectNode rootNode, String fieldName) {
    return MonetaryAmount.of(CliJsonMoneyParser.requiredPositiveMoney(rootNode, fieldName).money());
  }

  private static PostingLineage.Reversal readReversalObject(ObjectNode reversalObject) {
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolBusinessEventFields.Core.REVERSAL,
        ProtocolPostingNestedFieldSets.reversalFields());
    return new PostingLineage.Reversal(
        new ReversalReference(
            new PostingId(
                requiredText(reversalObject, ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID))),
        new ReversalReason(requiredText(reversalObject, ProtocolPostEntryFields.Reversal.REASON)));
  }
}
