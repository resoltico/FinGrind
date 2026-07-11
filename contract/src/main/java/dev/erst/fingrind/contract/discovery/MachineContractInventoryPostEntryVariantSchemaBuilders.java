package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Inventory acquisition and maintenance schema builders for post-entry requests. */
final class MachineContractInventoryPostEntryVariantSchemaBuilders {
  private MachineContractInventoryPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> purchaseSettledSchema() {
    return purchaseSchema(
        BookkeepingEntryKind.PURCHASE_SETTLED,
        "Settled purchase entry that acquires inventory quantity, debits an inventory account, and credits a cash account.",
        true,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account debited by this settled purchase."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this settled purchase."),
        true);
  }

  static Map<String, Object> purchaseOnCreditSchema() {
    return purchaseSchema(
        BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        "Purchase-on-credit entry that acquires inventory quantity, debits an inventory account, and credits a trade payable account.",
        true,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account debited by this purchase-on-credit."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account credited by this purchase-on-credit."),
        true);
  }

  static Map<String, Object> inventoryCapitalizationSettledSchema() {
    return MachineContractPostEntryTypedVariantSchemaBuilders.roleAmountSchema(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        "Settled inventory-capitalization entry that adds pre-VAT carrying cost to an existing inventory pool without changing quantity.",
        true,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account debited by this capitalization."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this capitalization."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional exclusive input-tax selector; recoverable tax stays outside inventory and nonrecoverable tax is capitalized.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> inventoryCapitalizationOnCreditSchema() {
    return MachineContractPostEntryTypedVariantSchemaBuilders.roleAmountSchema(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        "Inventory-capitalization-on-credit entry that adds pre-VAT carrying cost to an existing inventory pool without changing quantity.",
        true,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account debited by this capitalization."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
            "Declared trade payable account credited by this capitalization."),
        null,
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional exclusive input-tax selector; recoverable tax stays outside inventory and nonrecoverable tax is capitalized.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  static Map<String, Object> inventoryWriteDownSchema() {
    return MachineContractPostEntryTypedVariantSchemaBuilders.roleAmountSchema(
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        "Inventory write-down entry that decreases carrying cost without changing quantity.",
        false,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account credited by this write-down."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.WRITE_DOWN_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by this write-down."),
        null);
  }

  static Map<String, Object> inventoryShrinkageSchema() {
    return inventoryQuantitySchema(
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        "Inventory shrinkage entry that removes exact quantity and derives carrying cost from the pool.",
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account credited by this shrinkage."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.SHRINKAGE_LOSS_ACCOUNT_CODE,
            "Declared expense account debited by this shrinkage."));
  }

  static Map<String, Object> inventoryCountIncreaseSchema() {
    return purchaseSchema(
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        "Inventory count-increase entry that adds exact quantity at a supplied per-unit carrying cost.",
        false,
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
            "Declared inventory account debited by this count increase."),
        MachineContractPostEntryTypedVariantSchemaBuilders.requiredAccountField(
            ProtocolPostEntryFields.TopLevel.COUNT_GAIN_ACCOUNT_CODE,
            "Declared revenue account credited by this count increase."),
        false);
  }

  private static Map<String, Object> purchaseSchema(
      BookkeepingEntryKind entryKind,
      String description,
      boolean includeForeignExchange,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField,
      boolean taxAllowed) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(entryKind);
    List<MachineContractFieldSpec> fields = new java.util.ArrayList<>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, "This request records a typed business entry."));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField());
    fields.add(debitOrSourceAccountField);
    fields.add(creditOrTargetAccountField);
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredQuantityField());
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredUnitCostField());
    if (includeForeignExchange) {
      fields.add(
          MachineContractFieldSpec.optional(
              ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
              "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
              MachineContractPostEntryComponentSchemas.foreignExchangeSchema()));
    }
    if (taxAllowed) {
      fields.add(
          MachineContractFieldSpec.optional(
              ProtocolPostEntryFields.TopLevel.TAX,
              "Optional exclusive input-tax selector; recoverable tax stays outside inventory and nonrecoverable tax is capitalized.",
              MachineContractPostEntryComponentSchemas.taxSchema()));
    }
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts));
    fields.add(MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  private static Map<String, Object> inventoryQuantitySchema(
      BookkeepingEntryKind entryKind,
      String description,
      MachineContractFieldSpec inventoryAccountField,
      MachineContractFieldSpec lossAccountField) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        MachineContractPostEntryTypedVariantSchemaBuilders.entryKindFacts(entryKind);
    return MachineContractSchemaSupport.objectSchema(
        description,
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                entryKind, "This request records a typed inventory entry."),
            MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
            inventoryAccountField,
            lossAccountField,
            MachineContractPostEntryRequiredFieldSpecs.requiredQuantityField(),
            MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField()));
  }
}
